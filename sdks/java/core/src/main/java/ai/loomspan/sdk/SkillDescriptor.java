package ai.loomspan.sdk;

public record SkillDescriptor(String namespace, String localName, String description, String kind,
        String inputSchema, String outputSchema) {}
