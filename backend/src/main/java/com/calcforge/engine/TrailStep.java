package com.calcforge.engine;

import lombok.Builder;
import lombok.Value;

/** One entry in the transparent calculation trail shown to the user. */
@Value
@Builder
public class TrailStep {
    TrailStage stage;
    /** Short label, e.g. "Substitute variables", "Apply sin", "Step 3". */
    String title;
    /** Human-readable expression or state at this point in the reduction. */
    String expression;
    /** The value at this point, formatted as a string (null where not applicable, e.g. ASSUMPTIONS rows). */
    String value;
    /** Optional free-text explanation, e.g. "angle mode: DEGREES". */
    String note;
}
