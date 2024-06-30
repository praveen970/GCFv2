package com.gcfv2;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
import com.google.firebase.cloud.FirestoreClient;

import java.util.Map;

public class FirestoreServiceImpl implements IFirestoreService {
    private final Firestore db;

    public FirestoreServiceImpl() {
        this.db = FirestoreClient.getFirestore();
    }

    @Override
    public ApiFuture<WriteResult> saveDocument(String uniqueCode, Map<String, Object> data) {
        return db.collection("uniqueCodes").document(uniqueCode).set(data);
    }

    @Override
    public ApiFuture<DocumentSnapshot> getDocument(String documentId) {
        return db.collection("uniqueCodes").document(documentId).get();
    }

    @Override
    public ApiFuture<WriteResult> updateDocument(String documentId, Map<String, Object> updates) {
        return db.collection("uniqueCodes").document(documentId).update(updates);
    }
}
