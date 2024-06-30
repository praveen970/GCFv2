package com.gcfv2;

import com.google.api.core.ApiFuture;

public interface IPubSubService {
    void createPubSubTopicAndSubscription(String topicId, String subscriptionId);
    ApiFuture<Void> createPubSubTopicAsync(String topicId);
    ApiFuture<Void> createPubSubSubscriptionAsync(String topicId, String subscriptionId);
}
