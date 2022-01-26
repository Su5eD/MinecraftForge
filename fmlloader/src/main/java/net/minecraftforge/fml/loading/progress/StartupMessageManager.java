/*
 * Minecraft Forge
 * Copyright (c) 2016-2021.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package net.minecraftforge.fml.loading.progress;

import com.google.common.base.Ascii;
import com.google.common.base.CharMatcher;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class StartupMessageManager {

    public record MessageEntry(int age, Message message) {}

    private static volatile EnumMap<MessageType, List<Message>> messages = new EnumMap<>(MessageType.class);

    public static List<MessageEntry> getMessages() {
        final long ts = System.nanoTime();
        return messages.values().stream().flatMap(Collection::stream).
                sorted(Comparator.comparingLong(Message::getTimestamp).thenComparing(Message::getText).reversed()).
                map(m -> new MessageEntry((int) ((ts - m.timestamp) / 1e6), m)).
                limit(5).
                collect(Collectors.toList());
    }

    public static class Message {
        private final String text;
        private final MessageType type;
        private final long timestamp;

        public Message(final String text, final MessageType type) {
            this.text = text;
            this.type = type;
            this.timestamp = System.nanoTime();
        }

        public String getText() {
            return text;
        }

        MessageType getType() {
            return type;
        }

        long getTimestamp() {
            return timestamp;
        }

        public float[] getTypeColor(boolean isDarkMode) {
            return type.color(isDarkMode);
        }
    }

    enum MessageType {
        MC(1.0f, 1.0f, 1.0f), // Minecraft-related loading
        ML(0.0f, 0.0f, 0.5f, 0.0f, 0.8f, 1.0f), // Forge and mod-loading
        LOC(0.0f, 0.5f, 0.0f, 0.0f, 1.0f, 0.0f), // Locator and progress messages
        MOD(1.0f, 1.0f, 0.0f); // Mod messages

        private final float[] lightColor;
        private final float[] darkColor;

        MessageType(float r, float g, float b) {
            this(r, g, b, r, g, b);
        }
        
        MessageType(float lightR, float lightG, float lightB, float darkR, float darkG, float darkB) {
            lightColor = new float[] {lightR,lightG,lightB};
            darkColor = new float[] {darkR,darkG,darkB};
        }

        public float[] color(boolean isDarkMode) {
            return isDarkMode ? darkColor : lightColor;
        }
    }

    private synchronized static void addMessage(MessageType type, String message, int maxSize)
    {
        EnumMap<MessageType, List<Message>> newMessages = new EnumMap<>(messages);
        newMessages.compute(type, (key, existingList) -> {
            List<Message> newList = new ArrayList<>();
            if (existingList != null)
            {
                if (maxSize < 0)
                {
                    newList.addAll(existingList);
                }
                else
                {
                    newList.addAll(existingList.subList(0, Math.min(existingList.size(), maxSize)));
                }
            }
            newList.add(new Message(message, type));
            return newList;
        });
        messages = newMessages;
    }

    public static void addModMessage(final String message) {
        final String safeMessage = Ascii.truncate(CharMatcher.ascii().retainFrom(message),80,"~");
        addMessage(MessageType.MOD, safeMessage, 20);
    }

    public static Optional<Consumer<String>> modLoaderConsumer() {
        return Optional.of(s-> addMessage(MessageType.ML, s, -1));
    }

    public static Optional<Consumer<String>> locatorConsumer() {
        return Optional.of(s -> addMessage(MessageType.LOC, s, -1));
    }

    public static Optional<Consumer<String>> mcLoaderConsumer() {
        return Optional.of(s-> addMessage(MessageType.MC, s, -1));
    }
}
