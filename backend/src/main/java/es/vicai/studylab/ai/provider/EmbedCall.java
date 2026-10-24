package es.vicai.studylab.ai.provider;

import java.time.Duration;
import java.util.List;

public record EmbedCall(String model, List<String> inputs, Duration timeout) {}
