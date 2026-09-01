package com.calcforge.engine.blueprint;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"type", "op", "name", "value", "unit", "left", "right", "operand", "args"})
public record AstBlueprintNode(
        String type,
        String op,
        String name,
        String value,
        String unit,
        AstBlueprintNode left,
        AstBlueprintNode right,
        AstBlueprintNode operand,
        List<AstBlueprintNode> args
) {
    public static AstBlueprintNode literal(String value, String unit) {
        return new AstBlueprintNode("Literal", null, null, value, unit, null, null, null, null);
    }

    public static AstBlueprintNode literal(String value) {
        return literal(value, null);
    }

    public static AstBlueprintNode variable(String name) {
        return new AstBlueprintNode("Variable", null, name, null, null, null, null, null, null);
    }

    public static AstBlueprintNode operator(String op, AstBlueprintNode left, AstBlueprintNode right) {
        return new AstBlueprintNode("Operator", op, null, null, null, left, right, null, null);
    }

    public static AstBlueprintNode unary(String op, AstBlueprintNode operand) {
        return new AstBlueprintNode("Unary", op, null, null, null, null, null, operand, null);
    }

    public static AstBlueprintNode function(String name, List<AstBlueprintNode> args) {
        return new AstBlueprintNode("Function", null, name, null, null, null, null, null, args);
    }

    public static AstBlueprintNode factorial(AstBlueprintNode operand) {
        return new AstBlueprintNode("Factorial", null, null, null, null, null, null, operand, null);
    }

    public static AstBlueprintNode percent(AstBlueprintNode operand) {
        return new AstBlueprintNode("Percent", null, null, null, null, null, null, operand, null);
    }
}
