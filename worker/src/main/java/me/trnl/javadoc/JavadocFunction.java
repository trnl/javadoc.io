package me.trnl.javadoc;/*
 * Copyright (c) 2008 - 2012 10gen, Inc. <http://10gen.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.QueueingConsumer;

import java.util.Scanner;
import java.util.concurrent.Executors;

public class JavadocFunction {

    private static Reader reader = new Reader();

    public static final String QUEUE_NAME = "javadoc";

    public static void main(String... args) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("ec2-23-23-101-61.compute-1.amazonaws.com");
        Connection connection = factory.newConnection(Executors.newSingleThreadExecutor());
        Channel channel = connection.createChannel();

        channel.queueDeclare(QUEUE_NAME, true, false, false, null);
        System.out.println(" [*] Waiting for messages. To exit press CTRL+C");

        QueueingConsumer consumer = new QueueingConsumer(channel);
        channel.basicConsume(QUEUE_NAME, true, consumer);

        JavadocFunction function = new JavadocFunction();

        while (true) {
            QueueingConsumer.Delivery delivery = consumer.nextDelivery();
            String message = new String(delivery.getBody());
            System.out.println(" [x] Received '" + message + "'");
            function.work(message);
        }

    }

    public void work(String gav) throws Exception {
        Scanner sc = new Scanner(gav);
        sc.useDelimiter(":");

        String groupId = sc.next();
        String artifactId = sc.next();
        String version = sc.next();

        reader.perform(groupId, artifactId, version);
    }
}
