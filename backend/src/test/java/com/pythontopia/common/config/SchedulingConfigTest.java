package com.pythontopia.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class SchedulingConfigTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(SchedulingConfig.class);

  private final ApplicationContextRunner applicationContextRunner =
      new ApplicationContextRunner()
          .withInitializer(new ConfigDataApplicationContextInitializer())
          .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
          .withPropertyValues("pythontopia.scheduling.enabled=true")
          .withUserConfiguration(SchedulingConfig.class);

  @Test
  void schedulingIsEnabledByDefault() {
    contextRunner.run(
        context -> assertThat(context).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class));
  }

  @Test
  void schedulingCanBeDisabledForTests() {
    contextRunner
        .withPropertyValues("pythontopia.scheduling.enabled=false")
        .run(
            context ->
                assertThat(context).doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class));
  }

  @Test
  void schedulerHasWorkersForLongRunningJobsAndMaintenance() {
    applicationContextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(ThreadPoolTaskScheduler.class);
          assertThat(
                  context
                      .getBean(ThreadPoolTaskScheduler.class)
                      .getScheduledThreadPoolExecutor()
                      .getCorePoolSize())
              .isEqualTo(4);
        });
  }
}
