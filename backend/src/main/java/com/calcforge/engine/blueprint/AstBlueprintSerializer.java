package com.calcforge.engine.blueprint;

import com.calcforge.engine.NumberFormatter;
import com.calcforge.engine.ast.BinaryExpr;
import com.calcforge.engine.ast.Expr;
import com.calcforge.engine.ast.FactorialExpr;
import com.calcforge.engine.ast.FunctionCallExpr;
import com.calcforge.engine.ast.NumberExpr;
import com.calcforge.engine.ast.PercentExpr;
import com.calcforge.engine.ast.PhysicalValueExpr;
import com.calcforge.engine.ast.UnaryExpr;
import com.calcforge.engine.ast.VariableExpr;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.List;

public final class AstBlueprintSerializer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private AstBlueprintSerializer() {
    }

    public static AstBlueprintNode serialize(Expr expr) {
        if (expr == null) {
            return null;
        }
        if (expr instanceof NumberExpr e) {
            return AstBlueprintNode.literal(NumberFormatter.plain(e.getValue()));
        }
        if (expr instanceof PhysicalValueExpr e) {
            String unit = e.getPhysicalValue().getDimension().isDimensionless()
                    ? null
                    : e.getPhysicalValue().getDimension().toDerivedString();
            return AstBlueprintNode.literal(NumberFormatter.plain(e.getPhysicalValue().getValue()), unit);
        }
        if (expr instanceof VariableExpr e) {
            return AstBlueprintNode.variable(e.getName());
        }
        if (expr instanceof BinaryExpr e) {
            return AstBlueprintNode.operator(e.getOperator(), serialize(e.getLeft()), serialize(e.getRight()));
        }
        if (expr instanceof UnaryExpr e) {
            return AstBlueprintNode.unary(e.getOperator(), serialize(e.getOperand()));
        }
        if (expr instanceof PercentExpr e) {
            return AstBlueprintNode.percent(serialize(e.getOperand()));
        }
        if (expr instanceof FactorialExpr e) {
            return AstBlueprintNode.factorial(serialize(e.getOperand()));
        }
        if (expr instanceof FunctionCallExpr e) {
            List<AstBlueprintNode> args = e.getArgs().stream()
                    .map(AstBlueprintSerializer::serialize)
                    .toList();
            return AstBlueprintNode.function(e.getName(), args);
        }
        throw new IllegalArgumentException("Unsupported AST expression node type: " + expr.getClass().getName());
    }

    public static String toJson(Expr expr) {
        try {
            return OBJECT_MAPPER.writeValueAsString(serialize(expr));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize AST blueprint to JSON", e);
        }
    }
}
