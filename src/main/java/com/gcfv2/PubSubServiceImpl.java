package com.gcfv2;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.api.gax.rpc.AlreadyExistsException;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.cloud.pubsub.v1.TopicAdminClient;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PushConfig;
import com.google.pubsub.v1.Subscription;
import com.google.pubsub.v1.Topic;

import java.io.IOException;
import java.util.concurrent.Executors;

public class PubSubServiceImpl implements IPubSubService {

    @Override
    public void createPubSubTopicAndSubscription(String topicId, String subscriptionId) {
        try (TopicAdminClient topicAdminClient = TopicAdminClient.create()) {
            ProjectTopicName topicName = ProjectTopicName.of("mapzzz-261dd", topicId);
            Topic topic = topicAdminClient.createTopic(topicName);
            System.out.println("Created topic: " + topic.getName());

            try (SubscriptionAdminClient subscriptionAdminClient = SubscriptionAdminClient.create()) {
                ProjectSubscriptionName subscriptionName = ProjectSubscriptionName.of("mapzzz-261dd", subscriptionId);
                Subscription subscription = subscriptionAdminClient.createSubscription(
                        subscriptionName, topicName, PushConfig.getDefaultInstance(), 0);
                System.out.println("Created subscription: " + subscription.getName());
            }
        }catch (IOException e){
            System.out.println("Error creating Pubsub"+ e.getMessage());
        }
    }

    @Override
    public ApiFuture<Void> createPubSubTopicAsync(String topicId) {
        return ApiFutures.transform(
                ApiFutures.immediateFuture(null),
                ignored -> {
                    try (TopicAdminClient topicAdminClient = TopicAdminClient.create()) {
                        ProjectTopicName topicName = ProjectTopicName.of("mapzzz-261dd", topicId);
                        Topic topic = topicAdminClient.createTopic(topicName);
                        System.out.println("Created topic: " + topic.getName());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                },
                Executors.newCachedThreadPool()
        );
    }

    @Override
    public ApiFuture<Void> createPubSubSubscriptionAsync(String topicId, String subscriptionId) {
        return ApiFutures.transformAsync(
                ApiFutures.immediateFuture(null),
                ignored -> {
                    try (SubscriptionAdminClient subscriptionAdminClient = SubscriptionAdminClient.create()) {
                        ProjectTopicName topicName = ProjectTopicName.of("mapzzz-261dd", topicId);
                        ProjectSubscriptionName subscriptionName = ProjectSubscriptionName.of("mapzzz-261dd", subscriptionId);
                        try {
                            subscriptionAdminClient.createSubscription(subscriptionName, topicName, PushConfig.getDefaultInstance(), 0);
                            System.out.println("Created subscription: " + subscriptionName.toString());
                        } catch (AlreadyExistsException e) {
                            System.out.println("Subscription already exists: " + subscriptionName.toString());
                        }
                        return ApiFutures.immediateFuture(null);
                    } catch (IOException e) {
                        return ApiFutures.immediateFailedFuture(e);
                    }
                },
                Executors.newCachedThreadPool()
        );
    }
}
