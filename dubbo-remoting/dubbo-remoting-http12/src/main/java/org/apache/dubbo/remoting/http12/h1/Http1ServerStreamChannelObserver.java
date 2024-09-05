/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.remoting.http12.h1;

import org.apache.dubbo.remoting.http12.HttpChannel;
import org.apache.dubbo.remoting.http12.HttpConstants;
import org.apache.dubbo.remoting.http12.HttpHeaderNames;
import org.apache.dubbo.remoting.http12.HttpHeaders;
import org.apache.dubbo.remoting.http12.HttpMetadata;
import org.apache.dubbo.remoting.http12.HttpOutputMessage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class Http1ServerStreamChannelObserver extends Http1ServerChannelObserver {

    private static final byte[] SERVER_SENT_EVENT_DATA_PREFIX_BYTES = "data:".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] SERVER_SENT_EVENT_LF_BYTES = "\n\n".getBytes(StandardCharsets.US_ASCII);

    public Http1ServerStreamChannelObserver(HttpChannel httpChannel) {
        super(httpChannel);
    }

    @Override
    protected HttpMetadata encodeHttpMetadata(boolean endStream) {
        HttpHeaders headers = HttpHeaders.create();
        headers.set(HttpHeaderNames.TRANSFER_ENCODING.getKey(), HttpConstants.CHUNKED);
        return new Http1Metadata(headers);
    }

    @Override
    protected void preOutputMessage(HttpOutputMessage message) throws IOException {
        HttpOutputMessage prefixMessage = getHttpChannel().newOutputMessage();
        prefixMessage.getBody().write(SERVER_SENT_EVENT_DATA_PREFIX_BYTES);
        getHttpChannel().writeMessage(prefixMessage);
    }

    @Override
    protected void postOutputMessage(HttpOutputMessage message) throws IOException {
        HttpOutputMessage lfMessage = getHttpChannel().newOutputMessage();
        lfMessage.getBody().write(SERVER_SENT_EVENT_LF_BYTES);
        getHttpChannel().writeMessage(lfMessage);
    }
}
