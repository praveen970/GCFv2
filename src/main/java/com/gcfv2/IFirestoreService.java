package com.gcfv2;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.WriteResult;

import java.util.Map;

public interface IFirestoreService {
    ApiFuture<WriteResult> saveDocument(String uniqueCode, Map<String, Object> data);
    ApiFuture<DocumentSnapshot> getDocument(String documentId);
    ApiFuture<WriteResult> updateDocument(String documentId, Map<String, Object> updates);
}
