// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package org.springframework.samples.petclinic.customers.aws;

import org.json.JSONObject;
import org.json.JSONPointer;
import org.springframework.samples.petclinic.customers.Util;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.WebIdentityTokenFileCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;


@Component
public class BedrockService {

    final BedrockRuntimeClient bedrockClient;

    public BedrockService() {
        // AWS web identity is set for EKS clusters, if these are not set then use default credentials
        if (System.getenv("AWS_WEB_IDENTITY_TOKEN_FILE") == null && System.getProperty("aws.webIdentityTokenFile") == null) {
            bedrockClient = BedrockRuntimeClient.builder()
                    .region(Region.of(Util.REGION_FROM_EC2))
                    .build();
        } else {
            bedrockClient = BedrockRuntimeClient.builder()
                    .region(Region.of(Util.REGION_FROM_EKS))
                    .credentialsProvider(WebIdentityTokenFileCredentialsProvider.create())
                    .build();
        }
    }

    public String getConcernsForPetType(String petType) {

        String modelId = "amazon.titan-text-premier-v1:0";
        String prompt = "Human: Based on the '"+petType+"' identify the top 3 considerations that affect insurance rates for this type of pet.\\n\\nAssistant:";
        String nativeRequestTemplate = "{ \"inputText\": \"{{prompt}}\" }";

        // Embed the prompt in the model's native request payload.
        String nativeRequest = nativeRequestTemplate.replace("{{prompt}}", prompt);

        try {
            // Encode and send the request to the Bedrock Runtime.
            InvokeModelResponse response = bedrockClient.invokeModel(request -> request
                    .body(SdkBytes.fromUtf8String(nativeRequest))
                    .modelId(modelId)
            );

            // Decode the response body.
            JSONObject responseBody = new JSONObject(response.body().asUtf8String());

            // Retrieve the generated text from the model's response.
            return new JSONPointer("/results/0/outputText").queryFrom(responseBody).toString();
        } catch (SdkClientException e) {
            System.err.printf("ERROR: Can't invoke '%s'. Reason: %s", modelId, e.getMessage());
            return "Error retrieving typical issues with " + petType;
        }
    }

    public static void main(String[] args) {
        BedrockService service = new BedrockService();
        String resp = service.getConcernsForPetType("123");
        System.out.println("resp:" + resp);

    }
}
