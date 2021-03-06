/*
 * Copyright 2016-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.dataflow.server.config;

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ForkJoinPool;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.analytics.metrics.AggregateCounterRepository;
import org.springframework.analytics.metrics.FieldValueCounterRepository;
import org.springframework.analytics.metrics.redis.RedisMetricRepository;
import org.springframework.analytics.rest.controller.AggregateCounterController;
import org.springframework.analytics.rest.controller.CounterController;
import org.springframework.analytics.rest.controller.FieldValueCounterController;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.client.circuitbreaker.EnableCircuitBreaker;
import org.springframework.cloud.common.security.AuthorizationProperties;
import org.springframework.cloud.common.security.support.OnOAuth2SecurityEnabled;
import org.springframework.cloud.common.security.support.SecurityStateBean;
import org.springframework.cloud.dataflow.completion.CompletionConfiguration;
import org.springframework.cloud.dataflow.completion.StreamCompletionProvider;
import org.springframework.cloud.dataflow.completion.TaskCompletionProvider;
import org.springframework.cloud.dataflow.configuration.metadata.ApplicationConfigurationMetadataResolver;
import org.springframework.cloud.dataflow.registry.repository.AppRegistrationRepository;
import org.springframework.cloud.dataflow.registry.service.AppRegistryService;
import org.springframework.cloud.dataflow.registry.service.DefaultAppRegistryService;
import org.springframework.cloud.dataflow.registry.support.AppResourceCommon;
import org.springframework.cloud.dataflow.server.DockerValidatorProperties;
import org.springframework.cloud.dataflow.server.TaskValidationController;
import org.springframework.cloud.dataflow.server.batch.JobService;
import org.springframework.cloud.dataflow.server.config.apps.CommonApplicationProperties;
import org.springframework.cloud.dataflow.server.config.features.ConditionalOnStreamsEnabled;
import org.springframework.cloud.dataflow.server.config.features.ConditionalOnTasksEnabled;
import org.springframework.cloud.dataflow.server.config.features.FeaturesProperties;
import org.springframework.cloud.dataflow.server.controller.AboutController;
import org.springframework.cloud.dataflow.server.controller.AppRegistryController;
import org.springframework.cloud.dataflow.server.controller.AuditRecordController;
import org.springframework.cloud.dataflow.server.controller.CompletionController;
import org.springframework.cloud.dataflow.server.controller.JobExecutionController;
import org.springframework.cloud.dataflow.server.controller.JobInstanceController;
import org.springframework.cloud.dataflow.server.controller.JobStepExecutionController;
import org.springframework.cloud.dataflow.server.controller.JobStepExecutionProgressController;
import org.springframework.cloud.dataflow.server.controller.MetricsController;
import org.springframework.cloud.dataflow.server.controller.RestControllerAdvice;
import org.springframework.cloud.dataflow.server.controller.RootController;
import org.springframework.cloud.dataflow.server.controller.RuntimeAppInstanceController;
import org.springframework.cloud.dataflow.server.controller.RuntimeAppsController;
import org.springframework.cloud.dataflow.server.controller.StreamDefinitionController;
import org.springframework.cloud.dataflow.server.controller.StreamDeploymentController;
import org.springframework.cloud.dataflow.server.controller.StreamValidationController;
import org.springframework.cloud.dataflow.server.controller.TaskDefinitionController;
import org.springframework.cloud.dataflow.server.controller.TaskExecutionController;
import org.springframework.cloud.dataflow.server.controller.TaskSchedulerController;
import org.springframework.cloud.dataflow.server.controller.ToolsController;
import org.springframework.cloud.dataflow.server.controller.UiController;
import org.springframework.cloud.dataflow.server.controller.security.LoginController;
import org.springframework.cloud.dataflow.server.controller.security.SecurityController;
import org.springframework.cloud.dataflow.server.controller.support.MetricStore;
import org.springframework.cloud.dataflow.server.job.LauncherRepository;
import org.springframework.cloud.dataflow.server.repository.AuditRecordRepository;
import org.springframework.cloud.dataflow.server.repository.StreamDefinitionRepository;
import org.springframework.cloud.dataflow.server.repository.TaskDefinitionRepository;
import org.springframework.cloud.dataflow.server.service.AuditRecordService;
import org.springframework.cloud.dataflow.server.service.DefaultAuditRecordService;
import org.springframework.cloud.dataflow.server.service.SchedulerService;
import org.springframework.cloud.dataflow.server.service.SpringSecurityAuditorAware;
import org.springframework.cloud.dataflow.server.service.StreamService;
import org.springframework.cloud.dataflow.server.service.StreamValidationService;
import org.springframework.cloud.dataflow.server.service.TaskJobService;
import org.springframework.cloud.dataflow.server.service.TaskService;
import org.springframework.cloud.dataflow.server.service.TaskValidationService;
import org.springframework.cloud.dataflow.server.service.impl.AppDeploymentRequestCreator;
import org.springframework.cloud.dataflow.server.service.impl.DefaultStreamService;
import org.springframework.cloud.dataflow.server.service.impl.TaskConfigurationProperties;
import org.springframework.cloud.dataflow.server.service.impl.validation.DefaultStreamValidationService;
import org.springframework.cloud.dataflow.server.service.impl.validation.DefaultTaskValidationService;
import org.springframework.cloud.dataflow.server.stream.SkipperStreamDeployer;
import org.springframework.cloud.dataflow.server.stream.StreamDeployer;
import org.springframework.cloud.deployer.resource.maven.MavenProperties;
import org.springframework.cloud.deployer.resource.support.DelegatingResourceLoader;
import org.springframework.cloud.skipper.client.DefaultSkipperClient;
import org.springframework.cloud.skipper.client.SkipperClient;
import org.springframework.cloud.skipper.client.SkipperClientProperties;
import org.springframework.cloud.skipper.client.SkipperClientResponseErrorHandler;
import org.springframework.cloud.task.repository.TaskExplorer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.map.repository.config.EnableMapRepositories;
import org.springframework.hateoas.EntityLinks;
import org.springframework.hateoas.core.AnnotationRelProvider;
import org.springframework.hateoas.hal.HalConfiguration;
import org.springframework.hateoas.hal.Jackson2HalModule;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.scheduling.concurrent.ForkJoinPoolFactoryBean;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration for the Data Flow Server Controllers.
 *
 * @author Mark Fisher
 * @author Gunnar Hillert
 * @author Ilayaperumal Gopinathan
 * @author Andy Clement
 * @author Glenn Renfro
 * @author Christian Tzolov
 */
@SuppressWarnings("all")
@Configuration
@Import(CompletionConfiguration.class)
@ConditionalOnBean({ EnableDataFlowServerConfiguration.Marker.class })
@EnableConfigurationProperties({ FeaturesProperties.class, VersionInfoProperties.class, DockerValidatorProperties.class })
@ConditionalOnProperty(prefix = "dataflow.server", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableCircuitBreaker
@EntityScan({
		"org.springframework.cloud.dataflow.registry.domain",
		"org.springframework.cloud.dataflow.core"
})
@EnableMapRepositories("org.springframework.cloud.dataflow.server.job")
@EnableJpaRepositories(basePackages = {
		"org.springframework.cloud.dataflow.registry.repository",
		"org.springframework.cloud.dataflow.server.repository"
})
@EnableJpaAuditing
@EnableTransactionManagement
public class DataFlowControllerAutoConfiguration {

	private static Log logger = LogFactory.getLog(DataFlowControllerAutoConfiguration.class);

	@Bean
	public RootController rootController(EntityLinks entityLinks) {
		return new RootController(entityLinks);
	}

	@Bean
	public CompletionController completionController(StreamCompletionProvider completionProvider,
			TaskCompletionProvider taskCompletionProvider) {
		return new CompletionController(completionProvider, taskCompletionProvider);
	}

	@Bean
	public ToolsController toolsController() {
		return new ToolsController();
	}

	@Bean
	public AboutController aboutController(ObjectProvider<StreamDeployer> streamDeployer,
			ObjectProvider<LauncherRepository> launcherRepository,
			FeaturesProperties featuresProperties,
			VersionInfoProperties versionInfoProperties,
			SecurityStateBean securityStateBean) {
		return new AboutController(streamDeployer.getIfAvailable(), launcherRepository.getIfAvailable(),
				featuresProperties,
				versionInfoProperties,
				securityStateBean);
	}

	@Bean
	public UiController uiController() {
		return new UiController();
	}

	@Bean
	public RestControllerAdvice restControllerAdvice() {
		return new RestControllerAdvice();
	}

	@Configuration
	public static class AppRegistryConfiguration {

		@Bean
		@ConditionalOnMissingBean(name = "appRegistryFJPFB")
		public ForkJoinPoolFactoryBean appRegistryFJPFB() {
			ForkJoinPoolFactoryBean forkJoinPoolFactoryBean = new ForkJoinPoolFactoryBean();
			forkJoinPoolFactoryBean.setParallelism(4);
			return forkJoinPoolFactoryBean;
		}

		@Bean
		public AppResourceCommon appResourceCommon(MavenProperties mavenProperties, DelegatingResourceLoader delegatingResourceLoader) {
			return new AppResourceCommon(mavenProperties, delegatingResourceLoader);
		}

		@Bean
		public AppRegistryService appRegistryService(AppRegistrationRepository appRegistrationRepository,
				AppResourceCommon appResourceCommon) {
			return new DefaultAppRegistryService(appRegistrationRepository, appResourceCommon);
		}

		@Bean
		public AppRegistryController appRegistryController(
				Optional<StreamDefinitionRepository> streamDefinitionRepository,
				Optional<StreamService> streamService,
				AppRegistryService appRegistry, ApplicationConfigurationMetadataResolver metadataResolver,
				ForkJoinPool appRegistryFJPFB, MavenProperties mavenProperties) {
			return new AppRegistryController(streamDefinitionRepository,
					streamService,
					appRegistry,
					metadataResolver, appRegistryFJPFB, mavenProperties);
		}
	}

	@Configuration
	@ConditionalOnTasksEnabled
	public static class TaskEnabledConfiguration {

		@Bean
		public TaskExecutionController taskExecutionController(TaskExplorer explorer, TaskService taskService,
				TaskDefinitionRepository taskDefinitionRepository) {
			return new TaskExecutionController(explorer, taskService, taskDefinitionRepository);
		}

		@Bean
		public TaskDefinitionController taskDefinitionController(TaskExplorer taskExplorer,
				TaskDefinitionRepository repository,
				TaskService taskService) {
			return new TaskDefinitionController(taskExplorer, repository, taskService);
		}

		@Bean
		public JobExecutionController jobExecutionController(TaskJobService repository) {
			return new JobExecutionController(repository);
		}

		@Bean
		public JobStepExecutionController jobStepExecutionController(JobService service) {
			return new JobStepExecutionController(service);
		}

		@Bean
		public JobStepExecutionProgressController jobStepExecutionProgressController(JobService service) {
			return new JobStepExecutionProgressController(service);
		}

		@Bean
		public JobInstanceController jobInstanceController(TaskJobService repository) {
			return new JobInstanceController(repository);
		}

		@Bean
		public TaskValidationService taskValidationService(AppRegistryService appRegistry,
				DockerValidatorProperties dockerValidatorProperties,
				TaskDefinitionRepository taskDefinitionRepository,
				TaskConfigurationProperties taskConfigurationProperties) {
			return new DefaultTaskValidationService(appRegistry,
					dockerValidatorProperties,
					taskDefinitionRepository,
					taskConfigurationProperties.getComposedTaskRunnerName());
		}

		@Bean
		public TaskValidationController taskValidationController(TaskService taskService) {
			return new TaskValidationController(taskService);
		}
	}

	@Configuration
	@ConditionalOnStreamsEnabled
	@EnableConfigurationProperties(SkipperClientProperties.class)
	public static class StreamEnabledConfiguration {

		@Bean
		public StreamValidationService streamValidationService(AppRegistryService appRegistry,
				DockerValidatorProperties dockerValidatorProperties,
				StreamDefinitionRepository streamDefinitionRepository) {
			return new DefaultStreamValidationService(appRegistry,
					dockerValidatorProperties,
					streamDefinitionRepository);
		}

		@Bean
		public RuntimeAppInstanceController appInstanceController(StreamDeployer streamDeployer) {
			return new RuntimeAppInstanceController(streamDeployer);
		}

		@Bean
		public StreamDefinitionController streamDefinitionController(StreamDefinitionRepository repository,
				StreamService streamService) {
			return new StreamDefinitionController(streamService);
		}

		@Bean
		public StreamValidationController streamValidationController(StreamService streamService) {
			return new StreamValidationController(streamService);
		}

		@Bean
		public RuntimeAppsController runtimeAppsController(StreamDeployer streamDeployer) {
			return new RuntimeAppsController(streamDeployer);
		}

		@Bean
		@ConditionalOnMissingBean(name = "runtimeAppsStatusFJPFB")
		public ForkJoinPoolFactoryBean runtimeAppsStatusFJPFB() {
			ForkJoinPoolFactoryBean forkJoinPoolFactoryBean = new ForkJoinPoolFactoryBean();
			forkJoinPoolFactoryBean.setParallelism(8);
			return forkJoinPoolFactoryBean;
		}

		@Bean
		public StreamDeploymentController updatableStreamDeploymentController(
				StreamDefinitionRepository repository, StreamService streamService) {
			return new StreamDeploymentController(repository, streamService);
		}

		@Bean
		public SkipperClient skipperClient(SkipperClientProperties properties,
				RestTemplateBuilder restTemplateBuilder, ObjectMapper objectMapper) {

			// TODO (Tzolov) review the manual Hal convertion configuration
			objectMapper.registerModule(new Jackson2HalModule());
			objectMapper.setHandlerInstantiator(new Jackson2HalModule.HalHandlerInstantiator(
					new AnnotationRelProvider(), null, null, new HalConfiguration()));
			objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

			RestTemplate restTemplate = restTemplateBuilder
					.errorHandler(new SkipperClientResponseErrorHandler(objectMapper))
					.interceptors(new OAuth2AccessTokenProvidingClientHttpRequestInterceptor())
					.messageConverters(Arrays.asList(new StringHttpMessageConverter(),
							new MappingJackson2HttpMessageConverter(objectMapper)))
					.build();

			return new DefaultSkipperClient(properties.getServerUri(), restTemplate);
		}

		@Bean
		public SkipperStreamDeployer skipperStreamDeployer(SkipperClient skipperClient,
				StreamDefinitionRepository streamDefinitionRepository,
				SkipperClientProperties skipperClientProperties,
				AppRegistryService appRegistryService,
				ForkJoinPool runtimeAppsStatusFJPFB) {
			logger.info("Skipper URI [" + skipperClientProperties.getServerUri() + "]");
			return new SkipperStreamDeployer(skipperClient, streamDefinitionRepository, appRegistryService,
					runtimeAppsStatusFJPFB);
		}

		@Bean
		public AppDeploymentRequestCreator streamDeploymentPropertiesUtils(AppRegistryService appRegistry,
				CommonApplicationProperties commonApplicationProperties,
				ApplicationConfigurationMetadataResolver applicationConfigurationMetadataResolver) {
			return new AppDeploymentRequestCreator(appRegistry, commonApplicationProperties,
					applicationConfigurationMetadataResolver);
		}

		@Bean
		public StreamService streamService(
				StreamDefinitionRepository streamDefinitionRepository,
				SkipperStreamDeployer skipperStreamDeployer, AppDeploymentRequestCreator appDeploymentRequestCreator,
				StreamValidationService streamValidationService,
				AuditRecordService auditRecordService) {
			return new DefaultStreamService(streamDefinitionRepository, skipperStreamDeployer,
					appDeploymentRequestCreator, streamValidationService, auditRecordService);
		}
	}

	@Bean
	@ConditionalOnBean(SchedulerService.class)
	public TaskSchedulerController taskSchedulerController(SchedulerService schedulerService) {
		return new TaskSchedulerController(schedulerService);
	}

	@Configuration
	public static class AuditingConfiguration {
		@Bean
		public AuditRecordService auditRecordService(AuditRecordRepository auditRecordRepository,
				ObjectMapper objectMapper) {
			return new DefaultAuditRecordService(auditRecordRepository);
		}

		@Bean
		@ConditionalOnBean(AuditRecordService.class) // TODO Redundant ??
		public AuditRecordController auditController(AuditRecordService auditRecordService) {
			return new AuditRecordController(auditRecordService);
		}
	}

	@Configuration
	public static class SecurityConfiguration {

		@Bean
		public SpringSecurityAuditorAware springSecurityAuditorAware(SecurityStateBean securityStateBean) {
			return new SpringSecurityAuditorAware(securityStateBean);
		}

		@Bean
		public SecurityStateBean securityStateBean() {
			return new SecurityStateBean();
		}

		@Bean
		public SecurityController securityController(SecurityStateBean securityStateBean) {
			return new SecurityController(securityStateBean);
		}

		@Bean
		@ConfigurationProperties(prefix = "spring.cloud.dataflow.security.authorization")
		public AuthorizationProperties authorizationProperties() {
			return new AuthorizationProperties();
		}

		@Bean
		@Conditional(OnOAuth2SecurityEnabled.class)
		public LoginController loginController() {
			return new LoginController();
		}
	}

	@Configuration
	@EnableConfigurationProperties(MetricsProperties.class)
	public static class MetricsConfiguration {

		@Bean
		public MetricStore metricStore(MetricsProperties metricsProperties) {
			return new MetricStore(metricsProperties);
		}

		@Bean
		@ConditionalOnBean(RedisMetricRepository.class)
		public CounterController counterController(RedisMetricRepository metricRepository) {
			return new CounterController(metricRepository);
		}

		@Bean
		@ConditionalOnBean(FieldValueCounterRepository.class)
		public FieldValueCounterController fieldValueCounterController(FieldValueCounterRepository repository) {
			return new FieldValueCounterController(repository);
		}

		@Bean
		@ConditionalOnBean(AggregateCounterRepository.class)
		public AggregateCounterController aggregateCounterController(AggregateCounterRepository repository) {
			return new AggregateCounterController(repository);
		}

		@Bean
		public MetricsController metricsController(MetricStore metricStore) {
			return new MetricsController(metricStore);
		}
	}

}
