/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.undertow.util;

import io.undertow.UndertowMessages;
import io.undertow.server.HttpServerExchange;

import java.io.UnsupportedEncodingException;

/**
 * Utilities for dealing with URLs
 *
 * @author Stuart Douglas
 */
public class URLUtils {

    private static final char PATH_SEPARATOR = '/';

    private static final QueryStringParser QUERY_STRING_PARSER = new QueryStringParser() {
        @Override
        void handle(HttpServerExchange exchange, String key, String value) {
            exchange.addQueryParam(key, value);
        }
    };
    private static final QueryStringParser PATH_PARAM_PARSER = new QueryStringParser() {
        @Override
        void handle(HttpServerExchange exchange, String key, String value) {
            exchange.addPathParam(key, value);
        }
    };

    private URLUtils() {

    }

    public static void parseQueryString(final String string, final HttpServerExchange exchange, final String charset, final boolean doDecode, int maxParameters) throws ParameterLimitException {
        QUERY_STRING_PARSER.parse(string, exchange, charset, doDecode, maxParameters);
    }

    @Deprecated
    public static void parsePathParms(final String string, final HttpServerExchange exchange, final String charset, final boolean doDecode, int maxParameters) throws ParameterLimitException {
        parsePathParams(string, exchange, charset, doDecode, maxParameters);
    }

    public static void parsePathParams(final String string, final HttpServerExchange exchange, final String charset, final boolean doDecode, int maxParameters) throws ParameterLimitException {
        PATH_PARAM_PARSER.parse(string, exchange, charset, doDecode, maxParameters);
    }

    /**
     * Decodes a URL. If the decoding fails for any reason then an IllegalArgumentException will be thrown.
     *
     * @param s           The string to decode
     * @param enc         The encoding
     * @param decodeSlash If slash characters should be decoded
     * @param buffer      The string builder to use as a buffer.
     * @return The decoded URL
     */
    public static String decode(String s, String enc, boolean decodeSlash, StringBuilder buffer) {
        return decode(s, enc, decodeSlash, true, buffer);
    }

    /**
     * Decodes a URL. If the decoding fails for any reason then an IllegalArgumentException will be thrown.
     *
     * @param s           The string to decode
     * @param enc         The encoding
     * @param decodeSlash If slash characters should be decoded
     * @param buffer      The string builder to use as a buffer.
     * @return The decoded URL
     */
    public static String decode(String s, String enc, boolean decodeSlash, boolean formEncoding, StringBuilder buffer) {
        buffer.setLength(0);
        boolean needToChange = false;
        int numChars = s.length();
        int i = 0;

        while (i < numChars) {
            char c = s.charAt(i);
            if (c == '+') {
                if (formEncoding) {
                    buffer.append(' ');
                    i++;
                    needToChange = true;
                } else {
                    i++;
                    buffer.append(c);
                }
            } else if (c == '%' || c > 127) {
                /*
                 * Starting with this instance of a character
                 * that needs to be encoded, process all
                 * consecutive substrings of the form %xy. Each
                 * substring %xy will yield a byte. Convert all
                 * consecutive  bytes obtained this way to whatever
                 * character(s) they represent in the provided
                 * encoding.
                 *
                 * Note that we need to decode the whole rest of the value, we can't just decode
                 * three characters. For multi code point characters there if the code point can be
                 * represented as an alphanumeric
                 */
                try {
                    // guess the size of the remaining bytes
                    // of remaining bytes
                    // this works for percent encoded characters,
                    // not so much for unencoded bytes
                    byte[] bytes = new byte[numChars - i + 1];

                    int pos = 0;

                    while ((i < numChars)) {
                        if (c == '%') {
                            char p1 = Character.toLowerCase(s.charAt(i + 1));
                            char p2 = Character.toLowerCase(s.charAt(i + 2));
                            if (!decodeSlash && ((p1 == '2' && p2 == 'f') || (p1 == '5' && p2 == 'c'))) {
                                if(pos + 2 >= bytes.length) {
                                    bytes = expandBytes(bytes);
                                }
                                bytes[pos++] = (byte) c;
                                // should be copied with preserved upper/lower case
                                bytes[pos++] = (byte) s.charAt(i + 1);
                                bytes[pos++] = (byte) s.charAt(i + 2);
                                i += 3;

                                if (i < numChars) {
                                    c = s.charAt(i);
                                }
                                continue;
                            }
                            int v = 0;
                            if (p1 >= '0' && p1 <= '9') {
                                v = (p1 - '0') << 4;
                            } else if (p1 >= 'a' && p1 <= 'f') {
                                v = (p1 - 'a' + 10) << 4;
                            } else {
                                throw UndertowMessages.MESSAGES.failedToDecodeURL(s, enc, null);
                            }
                            if (p2 >= '0' && p2 <= '9') {
                                v += (p2 - '0');
                            } else if (p2 >= 'a' && p2 <= 'f') {
                                v += (p2 - 'a' + 10);
                            } else {
                                throw UndertowMessages.MESSAGES.failedToDecodeURL(s, enc, null);
                            }
                            if (v < 0) {
                                throw UndertowMessages.MESSAGES.failedToDecodeURL(s, enc, null);
                            }

                            if(pos == bytes.length) {
                                bytes = expandBytes(bytes);
                            }
                            bytes[pos++] = (byte) v;
                            i += 3;
                            if (i < numChars) {
                                c = s.charAt(i);
                            }
                        } else if (c == '+' && formEncoding) {
                            if(pos == bytes.length) {
                                bytes = expandBytes(bytes);
                            }
                            bytes[pos++] = (byte) ' ';
                            ++i;
                            if (i < numChars) {
                                c = s.charAt(i);
                            }
                        } else {
                            if (pos == bytes.length) {
                                bytes = expandBytes(bytes);
                            }
                            ++i;
                            if(c >> 8 != 0) {
                                bytes[pos++] = (byte) (c >> 8);
                                if (pos == bytes.length) {
                                    bytes = expandBytes(bytes);
                                }
                                bytes[pos++] = (byte) c;
                            } else {
                                bytes[pos++] = (byte) c;
                                if (i < numChars) {
                                    c = s.charAt(i);
                                }
                            }

                        }
                    }

                    String decoded = new String(bytes, 0, pos, enc);
                    buffer.append(decoded);
                } catch (NumberFormatException e) {
                    throw UndertowMessages.MESSAGES.failedToDecodeURL(s, enc, e);
                } catch (UnsupportedEncodingException e) {
                    throw UndertowMessages.MESSAGES.failedToDecodeURL(s, enc, e);
                }
                needToChange = true;
                break;
            } else {
                buffer.append(c);
                i++;
            }
        }

        return (needToChange ? buffer.toString() : s);
    }

    private static byte[] expandBytes(byte[] bytes) {
        byte[] newBytes = new byte[bytes.length + 10];
        System.arraycopy(bytes, 0, newBytes, 0, bytes.length);
        return newBytes;
    }

    private abstract static class QueryStringParser {

        void parse(final String string, final HttpServerExchange exchange, final String charset, final boolean doDecode, int max) throws ParameterLimitException {
            int count = 0;
            try {
                int stringStart = 0;
                String attrName = null;
                for (int i = 0; i < string.length(); ++i) {
                    char c = string.charAt(i);
                    if (c == '=' && attrName == null) {
                        attrName = string.substring(stringStart, i);
                        stringStart = i + 1;
                    } else if (c == '&') {
                        if (attrName != null) {
                            handle(exchange, decode(charset, attrName, doDecode), decode(charset, string.substring(stringStart, i), doDecode));
                            if(++count > max) {
                                throw UndertowMessages.MESSAGES.tooManyParameters(max);
                            }
                        } else {
                            handle(exchange, decode(charset, string.substring(stringStart, i), doDecode), "");
                            if(++count > max) {
                                throw UndertowMessages.MESSAGES.tooManyParameters(max);
                            }
                        }
                        stringStart = i + 1;
                        attrName = null;
                    }
                }
                if (attrName != null) {
                    handle(exchange, decode(charset, attrName, doDecode), decode(charset, string.substring(stringStart, string.length()), doDecode));
                    if(++count > max) {
                        throw UndertowMessages.MESSAGES.tooManyParameters(max);
                    }
                } else if (string.length() != stringStart) {
                    handle(exchange, decode(charset, string.substring(stringStart, string.length()), doDecode), "");
                    if(++count > max) {
                        throw UndertowMessages.MESSAGES.tooManyParameters(max);
                    }
                }
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }

        private String decode(String charset, String attrName, final boolean doDecode) throws UnsupportedEncodingException {
            if (doDecode) {
                return URLUtils.decode(attrName, charset, true, true, new StringBuilder());
            }
            return attrName;
        }

        abstract void handle(final HttpServerExchange exchange, final String key, final String value);
    }


    /**
     * Adds a '/' prefix to the beginning of a path if one isn't present
     * and removes trailing slashes if any are present.
     *
     * @param path the path to normalize
     * @return a normalized (with respect to slashes) result
     */
    public static String normalizeSlashes(final String path) {
        // prepare
        final StringBuilder builder = new StringBuilder(path);
        boolean modified = false;

        // remove all trailing '/'s except the first one
        while (builder.length() > 0 && builder.length() != 1 && PATH_SEPARATOR == builder.charAt(builder.length() - 1)) {
            builder.deleteCharAt(builder.length() - 1);
            modified = true;
        }

        // add a slash at the beginning if one isn't present
        if (builder.length() == 0 || PATH_SEPARATOR != builder.charAt(0)) {
            builder.insert(0, PATH_SEPARATOR);
            modified = true;
        }

        // only create string when it was modified
        if (modified) {
            return builder.toString();
        }

        return path;
    }
}
