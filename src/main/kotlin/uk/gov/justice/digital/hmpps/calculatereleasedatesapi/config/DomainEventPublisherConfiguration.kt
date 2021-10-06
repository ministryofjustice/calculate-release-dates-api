package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config

import com.amazonaws.services.sns.AmazonSNS
import com.google.gson.Gson
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.DomainEventPublisher
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.DomainEventPublisherImpl
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.StubDomainEventPublisher

@Configuration
class DomainEventPublisherConfiguration {
  @Bean("DomainEventPublisher")
  @ConditionalOnProperty(name = ["domain-events-sns.provider"], havingValue = "aws")
  fun awsDomainEventPublisher(
    @Qualifier("awsSnsClientForDomainEvents") client: AmazonSNS,
    @Value("\${domain-events-sns.topic.arn}") topicArn: String,
    gson: Gson,
  ): DomainEventPublisher = DomainEventPublisherImpl(client, topicArn, gson)

  @Bean("DomainEventPublisher")
  @ConditionalOnProperty(name = ["domain-events-sns.provider"], havingValue = "localstack")
  fun localStackDomainEventPublisher(): DomainEventPublisher = StubDomainEventPublisher()
}
