package com.calcforge.engine.blueprint;

import com.calcforge.engine.Parser;
import com.calcforge.engine.ast.Expr;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AstBlueprintSerializerTest {

    @Test
    void testUnitAndVariableMultiplication() {
        Expr expr = Parser.parse("50[kg] * gravity");
        AstBlueprintNode blueprint = AstBlueprintSerializer.serialize(expr);

        assertEquals("Operator", blueprint.type());
        assertEquals("*", blueprint.op());
        assertNotNull(blueprint.left());
        assertEquals("Literal", blueprint.left().type());
        assertEquals("50", blueprint.left().value());
        assertEquals("kg", blueprint.left().unit());
        assertNotNull(blueprint.right());
        assertEquals("Variable", blueprint.right().type());
        assertEquals("gravity", blueprint.right().name());
    }

    @Test
    void testParentheticalPrecedencePreservation() {
        Expr expr = Parser.parse("(a + b) * (c - d)");
        AstBlueprintNode blueprint = AstBlueprintSerializer.serialize(expr);

        assertEquals("Operator", blueprint.type());
        assertEquals("*", blueprint.op());
        assertEquals("+", blueprint.left().op());
        assertEquals("Variable", blueprint.left().left().type());
        assertEquals("a", blueprint.left().left().name());
        assertEquals("b", blueprint.left().right().name());

        assertEquals("-", blueprint.right().op());
        assertEquals("c", blueprint.right().left().name());
        assertEquals("d", blueprint.right().right().name());
    }

    @Test
    void testNestedFunctionCallHierarchy() {
        Expr expr = Parser.parse("sqrt(x^2 + y^2)");
        AstBlueprintNode blueprint = AstBlueprintSerializer.serialize(expr);

        assertEquals("Function", blueprint.type());
        assertEquals("sqrt", blueprint.name());
        assertEquals(1, blueprint.args().size());

        AstBlueprintNode arg = blueprint.args().get(0);
        assertEquals("Operator", arg.type());
        assertEquals("+", arg.op());
        assertEquals("^", arg.left().op());
        assertEquals("^", arg.right().op());
    }

    @Test
    void testJsonOutputFormat() {
        Expr expr = Parser.parse("50[kg] * gravity");
        String json = AstBlueprintSerializer.toJson(expr);

        assertNotNull(json);
        org.junit.jupiter.api.Assertions.assertTrue(json.contains("\"type\" : \"Operator\""));
        org.junit.jupiter.api.Assertions.assertTrue(json.contains("\"op\" : \"*\""));
        org.junit.jupiter.api.Assertions.assertTrue(json.contains("\"unit\" : \"kg\""));
        org.junit.jupiter.api.Assertions.assertTrue(json.contains("\"name\" : \"gravity\""));
    }
}
