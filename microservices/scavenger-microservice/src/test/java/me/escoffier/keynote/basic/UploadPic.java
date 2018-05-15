package me.escoffier.keynote.basic;

import io.vertx.reactivex.core.Vertx;

import java.io.IOException;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class UploadPic {

    public static final String AWS = "wss://scavenger-hunt-microservice-scavenger-hunt-microservice.apps.summit-aws.sysdeseng.com";
    public static final String GCE = "wss://scavenger-hunt-microservice-scavenger-hunt-microservice.apps.summit-gce" +
        ".sysdeseng.com";
    public static final String LOCAL = "ws://localhost:8080";

    public static final String AWS_2 = "wss://scavenger-hunt-microservice-scavenger-killer-project.apps.summit-aws" +
        ".sysdeseng.com";


    public static void main(String[] args) throws IOException {
       Vertx vertx = Vertx.vertx();

       FakeUser user = new FakeUser();
       user.run(AWS, "openshift/aws/config.json");

    }

}
