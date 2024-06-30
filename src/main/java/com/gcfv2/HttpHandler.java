package com.gcfv2;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.WriteResult;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class HttpHandler implements HttpFunction {
    private static final Logger logger = Logger.getLogger(HttpHandler.class.getName());
    private final IFirestoreService firestoreService = new FirestoreServiceImpl();
    private final IPubSubService pubSubService = new PubSubServiceImpl();

    static {
        try {
            // Initialize Firebase
            initialize();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize Firestore", e);
        }
    }

    private static void initialize() throws IOException {
        boolean isRunningLocally = System.getenv("FIREBASE_CONFIG") == null;

        if (isRunningLocally) {
            FileInputStream serviceAccount = new FileInputStream("config/pbkey.json");
            FirebaseOptions options = new FirebaseOptions.Builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();
            FirebaseApp.initializeApp(options);
        } else {
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp();
            }
        }
    }

    @Override
    public void service(HttpRequest httpRequest, HttpResponse httpResponse) throws Exception {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(httpResponse.getOutputStream(), StandardCharsets.UTF_8));

        switch (httpRequest.getMethod()) {
            case "GET":
                handleGetRequest(httpRequest, httpResponse);
                break;
            case "POST":
                handlePostRequest(httpRequest, httpResponse);
                break;
            default:
                httpResponse.setStatusCode(405); // Method Not Allowed
                writer.write("Unsupported method");
                writer.flush();
        }
    }

    private void handleGetRequest(HttpRequest request, HttpResponse response) throws IOException {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8));
        String code = request.getFirstQueryParameter("code").orElse("");
        String id = request.getFirstQueryParameter("id").orElse("");

        if (code.isEmpty()) {
            response.setStatusCode(HttpURLConnection.HTTP_BAD_REQUEST);
            writer.write("No code provided.");
            writer.flush();
            return;
        }

        DocumentSnapshot documentSnapshot = null;
        try {
            documentSnapshot = firestoreService.getDocument(code).get();
        } catch (InterruptedException | ExecutionException e) {
            response.setStatusCode(HttpURLConnection.HTTP_INTERNAL_ERROR);
            writer.write("An error occurred: " + e.getMessage());
            writer.flush();
            return;
        }

        try {
            if (documentSnapshot.exists()) {
                Boolean isActive = documentSnapshot.getBoolean("active");
                if (isActive != null) {
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("active", true);
                    response.setContentType("application/json");
                    writer.write("{\"message\": \"Code is active.\", \"valid\": true}");
                    firestoreService.updateDocument(code, updates);
                    String subscriptionId = "subscription-" + code + "-" + id;
                    ApiFuture<Void> subscriptionFuture = pubSubService.createPubSubSubscriptionAsync("topic-" + code, subscriptionId);

                    ApiFutures.addCallback(subscriptionFuture, new ApiFutureCallback<Void>() {
                        @Override
                        public void onFailure(Throwable t) {
                            try {
                                response.setStatusCode(HttpURLConnection.HTTP_INTERNAL_ERROR);
                                writer.write("An error occurred: " + t.getMessage());
                                writer.flush();
                            } catch (IOException e) {
                                logger.severe("Failed to write response: " + e.getMessage());
                            }
                        }

                        @Override
                        public void onSuccess(Void result) {
                            try {
                                response.setContentType("application/json");
                                writer.write("{\"message\": \"Code is verified and activated.\", \"valid\": true}");
                                writer.flush();
                            } catch (IOException e) {
                                logger.severe("Failed to write response: " + e.getMessage());
                            }
                        }
                    }, Executors.newCachedThreadPool());
                    writer.flush();
                } else {
                    response.setContentType("application/json");
                    writer.write("{\"message\": \"Code is invalid.\", \"valid\": false}");
                    writer.flush();
                }
            } else {
                response.setContentType("application/json");
                writer.write("{\"message\": \"Code does not exist.\", \"valid\": false}");
                writer.flush();
            }
        } catch (Exception e) {
            try {
                response.setStatusCode(HttpURLConnection.HTTP_INTERNAL_ERROR);
                writer.write("An error occurred: " + e.getMessage());
                writer.flush();
            } catch (IOException ioException) {
                logger.severe("Failed to write response: " + ioException.getMessage());
            }
        }
    }

    private void handlePostRequest(HttpRequest request, HttpResponse response) throws Exception {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8));
        String uniqueCode = generateUniqueCode();

        String id = request.getFirstQueryParameter("id").orElse("");

        Map<String, Object> data = new HashMap<>();
        data.put("code", uniqueCode);
        data.put("createdAt", System.currentTimeMillis());
        data.put("active", false);

        // Firestore operation
        ApiFuture<WriteResult> firestoreFuture = firestoreService.saveDocument(uniqueCode, data);
        writer.write(uniqueCode);
        writer.flush();
        ApiFutures.addCallback(firestoreFuture, new ApiFutureCallback<WriteResult>() {
            @Override
            public void onFailure(Throwable t) {
                try {
                    response.setStatusCode(500);
                    writer.write("Failed to create resources. Error: " + t.getMessage());
                    writer.flush();
                } catch (IOException e) {
                    logger.severe("Failed to write response: " + e.getMessage());
                }
            }

            @Override
            public void onSuccess(WriteResult result) {
                try {
                    pubSubService.createPubSubTopicAndSubscription("topic-" + uniqueCode, "subscription-" + uniqueCode + "-" + id);
//                    response.setStatusCode(200);
//                    writer.write(uniqueCode); // Return just the unique code
//                    writer.flush();
//                    System.out.println("Generated code: " + uniqueCode + " with Pub/Sub topic and subscription.");
                } catch (Exception e) {
                    response.setStatusCode(500);
                }
            }
        }, Executors.newCachedThreadPool());
    }

    private String generateUniqueCode() throws ExecutionException, InterruptedException {
        Random random = new Random();
        String uniqueCode;
        do {
            uniqueCode = String.format("%06d", random.nextInt(999999));
        } while (firestoreService.getDocument(uniqueCode).get().exists());
        return uniqueCode;
    }
}
