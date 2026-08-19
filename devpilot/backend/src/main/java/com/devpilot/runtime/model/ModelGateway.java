package com.devpilot.runtime.model;

/**
 * The runtime's only way to reach a language model.
 *
 * <p>Agents, tools and application services depend on this interface, never on a vendor SDK, so a
 * provider can be replaced without touching a prompt or a controller. Implementations resolve
 * credentials themselves from {@link ModelRequest#credentialRef()} and must report the metadata in
 * {@link ModelCallMetadata} even when a call fails.
 */
public interface ModelGateway {

    /**
     * Runs a model request and waits for the complete answer.
     *
     * @param request conversation, tools and routing
     * @return final answer with provider metadata
     */
    ModelResponse call(ModelRequest request);

    /**
     * Runs a model request and reports output as it arrives.
     *
     * @param request conversation, tools and routing
     * @param listener receiver of incremental output
     * @return final answer with provider metadata
     */
    ModelResponse stream(ModelRequest request, ModelStreamListener listener);
}
