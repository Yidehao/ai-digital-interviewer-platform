package org.interviewer.orchestrator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

import java.nio.charset.StandardCharsets;

/**
 * Subscribes this node to answers forwarded by other nodes.
 *
 * <p>One channel per node rather than a shared channel every process filters. A shared channel
 * works and wakes every node for every answer, which at the concurrency this app sustains is a
 * steady stream of messages each node exists only to discard.
 *
 * <p>The container runs its own threads, deliberately not the agent pool: a listener that blocked
 * on a full agent pool would stop delivering answers to interviews already running, which is the
 * opposite of what back-pressure should do.
 */
@Slf4j
@Configuration
public class AnswerSubscriberConfig {

    @Bean
    public RedisMessageListenerContainer answerListenerContainer(
            RedisConnectionFactory connectionFactory, AnswerRouter router) {

        MessageListenerAdapter adapter = new MessageListenerAdapter(
                (org.springframework.data.redis.connection.MessageListener) (message, pattern) ->
                        router.receive(new String(message.getBody(), StandardCharsets.UTF_8)));

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(adapter, new ChannelTopic(router.channel()));
        log.info("listening for forwarded answers on {}", router.channel());
        return container;
    }
}
