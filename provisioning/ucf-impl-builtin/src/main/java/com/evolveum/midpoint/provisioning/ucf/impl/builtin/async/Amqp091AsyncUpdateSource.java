/*
 * Copyright (c) 2010-2019 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evolveum.midpoint.provisioning.ucf.impl.builtin.async;

import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.crypto.EncryptionException;
import com.evolveum.midpoint.provisioning.ucf.api.AsyncUpdateMessageListener;
import com.evolveum.midpoint.provisioning.ucf.api.AsyncUpdateSource;
import com.evolveum.midpoint.provisioning.ucf.api.ListeningActivity;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SystemException;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.Amqp091MessageType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.Amqp091SourceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AsyncUpdateSourceType;
import com.rabbitmq.client.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeoutException;

/**
 *  Async Update source for AMQP 0.9.1 brokers.
 */
public class Amqp091AsyncUpdateSource implements AsyncUpdateSource {

	private static final Trace LOGGER = TraceManager.getTrace(Amqp091AsyncUpdateSource.class);

	@NotNull private final Amqp091SourceType sourceConfiguration;
	@NotNull private final PrismContext prismContext;
	@NotNull private final ConnectionFactory connectionFactory;

	private Amqp091AsyncUpdateSource(@NotNull Amqp091SourceType sourceConfiguration, @NotNull AsyncUpdateConnectorInstance connectorInstance) {
		this.sourceConfiguration = sourceConfiguration;
		this.prismContext = connectorInstance.getPrismContext();
		this.connectionFactory = createConnectionFactory();
	}

	private class ListeningActivityImpl implements ListeningActivity {

		private Connection activeConnection;
		private Channel activeChannel;

		@Override
		public void stop() {
			silentlyCloseActiveConnection();
		}

		private void startListening(AsyncUpdateMessageListener listener) {
			try {
				activeConnection = connectionFactory.newConnection();
				activeChannel = activeConnection.createChannel();
				DeliverCallback deliverCallback = (consumerTag, message) -> {
					byte[] body = message.getBody();
					System.out.println("Body: " + new String(body, StandardCharsets.UTF_8));
					try {
						boolean successful = listener.onMessage(createAsyncUpdateMessage(message));
						if (successful) {
							activeChannel.basicAck(message.getEnvelope().getDeliveryTag(), false);
						} else {
							rejectMessage(message);
						}
					} catch (RuntimeException | SchemaException e) {
						LoggingUtils.logUnexpectedException(LOGGER, "Got exception while processing message", e);
						rejectMessage(message);
					}
				};
				String activeConsumerTag = activeChannel
						.basicConsume(sourceConfiguration.getQueue(), false, deliverCallback, consumerTag -> {});
				System.out.println("Opened consumer " + activeConsumerTag);
			} catch (Throwable t) {
				LoggingUtils.logUnexpectedException(LOGGER, "Exception on AMQP", t);
				if (activeConnection != null) {
					silentlyCloseActiveConnection();
				}
			}
		}

		private void rejectMessage(Delivery message) throws IOException {
			// TODO implement a policy to selectively requeue or discard messages
			activeChannel.basicReject(message.getEnvelope().getDeliveryTag(), true);
		}

		private void silentlyCloseActiveConnection() {
			try {
				System.out.println("Closing " + activeConnection);
				activeConnection.close();
			} catch (Throwable t) {
				LoggingUtils.logUnexpectedException(LOGGER, "Couldn't close active connection {}", t, activeConnection);
			}
			activeConnection = null;
		}
	}

	private Amqp091MessageType createAsyncUpdateMessage(Delivery message) {
		return new Amqp091MessageType()
				.sourceName(sourceConfiguration.getName())
				.body(message.getBody());
		// todo other attributes here
	}

	public static Amqp091AsyncUpdateSource create(AsyncUpdateSourceType configuration, AsyncUpdateConnectorInstance connectorInstance) {
		if (!(configuration instanceof Amqp091SourceType)) {
			throw new IllegalArgumentException("AMQP source requires " + Amqp091SourceType.class.getName() + " but got " +
					configuration.getClass().getName());
		}
		return new Amqp091AsyncUpdateSource((Amqp091SourceType) configuration, connectorInstance);
	}

	@Override
	public ListeningActivity startListening(AsyncUpdateMessageListener listener) {
		ListeningActivityImpl listeningActivity = new ListeningActivityImpl();
		listeningActivity.startListening(listener);
		return listeningActivity;
	}

	@NotNull
	private ConnectionFactory createConnectionFactory() {
		try {
			ConnectionFactory connectionFactory = new ConnectionFactory();
			connectionFactory.setUri(sourceConfiguration.getUri());
			connectionFactory.setUsername(sourceConfiguration.getUsername());
			if (sourceConfiguration.getPassword() != null) {
				connectionFactory.setPassword(prismContext.getDefaultProtector().decryptString(sourceConfiguration.getPassword()));
			}
			if (sourceConfiguration.getVirtualHost() != null) {
				connectionFactory.setVirtualHost(sourceConfiguration.getVirtualHost());
			}
			return connectionFactory;
		} catch (URISyntaxException | NoSuchAlgorithmException | KeyManagementException | EncryptionException e) {
			throw new SystemException("Couldn't create connection factory: " + e.getMessage(), e);
		}
	}

	@Override
	public void test(OperationResult parentResult) {
		OperationResult result = parentResult.createSubresult(getClass().getName() + ".test");
		result.addParam("sourceName", sourceConfiguration.getName());
		try (Connection connection = connectionFactory.newConnection();
				Channel channel = connection.createChannel()) {
			LOGGER.info("Connection and channel created OK: {}", channel);
			int messageCount = channel.queueDeclarePassive(sourceConfiguration.getQueue()).getMessageCount();
			LOGGER.info("# of messages in queue {}: {}", sourceConfiguration.getQueue(), messageCount);
			result.recordSuccess();
		} catch (TimeoutException | IOException e) {
			result.recordFatalError("Couldn't connect to AMQP queue: " + e.getMessage(), e);
			throw new SystemException("Couldn't connect to AMQP queue: " + e.getMessage(), e);
		}
	}

}
