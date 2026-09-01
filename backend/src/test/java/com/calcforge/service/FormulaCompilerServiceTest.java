package com.calcforge.service;

import com.calcforge.engine.Parser;
import com.calcforge.engine.ast.Expr;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormulaCompilerServiceTest {

    private final FormulaCompilerService compilerService = new FormulaCompilerService();

    @Test
    void testCompileToPython() {
        Expr expr = Parser.parse("50[kg] * gravity");
        String pyCode = compilerService.compileToPython(expr);

        assertNotNull(pyCode);
        assertTrue(pyCode.contains("from decimal import Decimal"));
        assertTrue(pyCode.contains("gravity = Decimal(str(params.get('gravity', '0')))"));
        assertTrue(pyCode.contains("(Decimal('50') * gravity)"));
    }

    @Test
    void testCompileToJava() {
        Expr expr = Parser.parse("50[kg] * gravity");
        String javaCode = compilerService.compileToJava(expr);

        assertNotNull(javaCode);
        assertTrue(javaCode.contains("import java.math.BigDecimal;"));
        assertTrue(javaCode.contains("import java.math.MathContext;"));
        assertTrue(javaCode.contains("BigDecimal gravity = params.getOrDefault(\"gravity\", BigDecimal.ZERO);"));
        assertTrue(javaCode.contains("new BigDecimal(\"50\").multiply(gravity, MC)"));
    }

    @Test
    void testCompileToRust() {
        Expr expr = Parser.parse("50[kg] * gravity");
        String rustCode = compilerService.compileToRust(expr);

        assertNotNull(rustCode);
        assertTrue(rustCode.contains("use num_bigint::BigInt;"));
        assertTrue(rustCode.contains("let gravity = params.get(\"gravity\")"));
        assertTrue(rustCode.contains("(BigInt::from(50i64) * gravity)"));
    }

    @Test
    void testComplexChainedPrecedence() {
        Expr expr = Parser.parse("(mass * acceleration) + (friction / 2)");
        String javaCode = compilerService.compileToJava(expr);

        assertTrue(javaCode.contains("BigDecimal mass = params.getOrDefault(\"mass\", BigDecimal.ZERO);"));
        assertTrue(javaCode.contains("BigDecimal acceleration = params.getOrDefault(\"acceleration\", BigDecimal.ZERO);"));
        assertTrue(javaCode.contains("BigDecimal friction = params.getOrDefault(\"friction\", BigDecimal.ZERO);"));
        assertTrue(javaCode.contains(".add("));
        assertTrue(javaCode.contains(".multiply("));
        assertTrue(javaCode.contains(".divide("));
    }
}
