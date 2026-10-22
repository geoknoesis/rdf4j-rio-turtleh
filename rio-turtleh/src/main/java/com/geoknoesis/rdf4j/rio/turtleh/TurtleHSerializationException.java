package com.geoknoesis.rdf4j.rio.turtleh;

import org.eclipse.rdf4j.rio.RDFHandlerException;

/** Raised when Turtle-H cannot preserve the supplied graph and authoritative metadata context. */
public final class TurtleHSerializationException extends RDFHandlerException {
    private static final long serialVersionUID = 1L;

    /**
     * Creates a serialization failure with the reason the context cannot be represented.
     *
     * @param message the reason the context cannot be represented
     */
    public TurtleHSerializationException(String message) {
        super(message);
    }
}
