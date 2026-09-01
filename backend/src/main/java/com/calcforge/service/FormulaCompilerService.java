package com.calcforge.service;

import com.calcforge.engine.blueprint.AstBlueprintNode;
import com.calcforge.engine.blueprint.AstBlueprintSerializer;
import com.calcforge.engine.ast.Expr;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class FormulaCompilerService {

    public String compileToPython(AstBlueprintNode root) {
        if (root == null) {
            return "";
        }
        Set<String> vars = new LinkedHashSet<>();
        collectVariables(root, vars);

        StringBuilder sb = new StringBuilder();
        sb.append("from decimal import Decimal, getcontext\n\n");
        sb.append("getcontext().prec = 28\n\n");
        sb.append("def calculate(params: dict = None) -> Decimal:\n");
        sb.append("    if params is None:\n");
        sb.append("        params = {}\n");

        for (String v : vars) {
            String sanitized = sanitizeIdentifier(v);
            sb.append("    ").append(sanitized).append(" = Decimal(str(params.get('").append(v).append("', '0')))\n");
        }

        sb.append("    return ").append(renderPythonExpr(root)).append("\n");
        return sb.toString();
    }

    public String compileToPython(Expr expr) {
        return compileToPython(AstBlueprintSerializer.serialize(expr));
    }

    public String compileToJava(AstBlueprintNode root) {
        if (root == null) {
            return "";
        }
        Set<String> vars = new LinkedHashSet<>();
        collectVariables(root, vars);

        StringBuilder sb = new StringBuilder();
        sb.append("package generated;\n\n");
        sb.append("import java.math.BigDecimal;\n");
        sb.append("import java.math.MathContext;\n");
        sb.append("import java.math.RoundingMode;\n");
        sb.append("import java.util.Map;\n\n");
        sb.append("public final class GeneratedFormula {\n");
        sb.append("    private static final MathContext MC = new MathContext(28, RoundingMode.HALF_UP);\n\n");
        sb.append("    public static BigDecimal calculate(Map<String, BigDecimal> params) {\n");
        sb.append("        if (params == null) {\n");
        sb.append("            params = Map.of();\n");
        sb.append("        }\n");

        for (String v : vars) {
            String sanitized = sanitizeIdentifier(v);
            sb.append("        BigDecimal ").append(sanitized)
                    .append(" = params.getOrDefault(\"").append(v).append("\", BigDecimal.ZERO);\n");
        }

        sb.append("        return ").append(renderJavaExpr(root)).append(";\n");
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }

    public String compileToJava(Expr expr) {
        return compileToJava(AstBlueprintSerializer.serialize(expr));
    }

    public String compileToRust(AstBlueprintNode root) {
        if (root == null) {
            return "";
        }
        Set<String> vars = new LinkedHashSet<>();
        collectVariables(root, vars);

        StringBuilder sb = new StringBuilder();
        sb.append("use std::collections::HashMap;\n");
        sb.append("use num_bigint::BigInt;\n");
        sb.append("use num_traits::Zero;\n\n");
        sb.append("pub fn calculate(params: &HashMap<String, BigInt>) -> BigInt {\n");

        for (String v : vars) {
            String sanitized = sanitizeIdentifier(v);
            sb.append("    let ").append(sanitized)
                    .append(" = params.get(\"").append(v).append("\").cloned().unwrap_or_else(BigInt::zero);\n");
        }

        sb.append("    ").append(renderRustExpr(root)).append("\n");
        sb.append("}\n");
        return sb.toString();
    }

    public String compileToRust(Expr expr) {
        return compileToRust(AstBlueprintSerializer.serialize(expr));
    }

    private String renderPythonExpr(AstBlueprintNode node) {
        if (node == null) return "Decimal('0')";
        String type = node.type();
        if ("Literal".equals(type)) {
            return "Decimal('" + node.value() + "')";
        }
        if ("Variable".equals(type)) {
            return sanitizeIdentifier(node.name());
        }
        if ("Operator".equals(type)) {
            String left = renderPythonExpr(node.left());
            String right = renderPythonExpr(node.right());
            String op = node.op();
            if ("^".equals(op)) {
                return "(" + left + " ** " + right + ")";
            }
            return "(" + left + " " + op + " " + right + ")";
        }
        if ("Unary".equals(type)) {
            return "(" + node.op() + renderPythonExpr(node.operand()) + ")";
        }
        if ("Percent".equals(type)) {
            return "(" + renderPythonExpr(node.operand()) + " / Decimal('100'))";
        }
        if ("Function".equals(type)) {
            String name = node.name().toLowerCase();
            if ("sqrt".equals(name) && node.args() != null && !node.args().isEmpty()) {
                return "(" + renderPythonExpr(node.args().get(0)) + ".sqrt())";
            }
            if ("abs".equals(name) && node.args() != null && !node.args().isEmpty()) {
                return "abs(" + renderPythonExpr(node.args().get(0)) + ")";
            }
            if (node.args() != null && !node.args().isEmpty()) {
                return "(" + renderPythonExpr(node.args().get(0)) + ")";
            }
        }
        return "Decimal('0')";
    }

    private String renderJavaExpr(AstBlueprintNode node) {
        if (node == null) return "BigDecimal.ZERO";
        String type = node.type();
        if ("Literal".equals(type)) {
            return "new BigDecimal(\"" + node.value() + "\")";
        }
        if ("Variable".equals(type)) {
            return sanitizeIdentifier(node.name());
        }
        if ("Operator".equals(type)) {
            String left = renderJavaExpr(node.left());
            String right = renderJavaExpr(node.right());
            return switch (node.op()) {
                case "+" -> left + ".add(" + right + ", MC)";
                case "-" -> left + ".subtract(" + right + ", MC)";
                case "*" -> left + ".multiply(" + right + ", MC)";
                case "/" -> left + ".divide(" + right + ", MC)";
                case "^" -> left + ".pow(" + right + ".intValueExact(), MC)";
                case "%" -> left + ".remainder(" + right + ", MC)";
                default -> left + ".add(" + right + ", MC)";
            };
        }
        if ("Unary".equals(type)) {
            String operand = renderJavaExpr(node.operand());
            return "-".equals(node.op()) ? operand + ".negate(MC)" : operand + ".plus(MC)";
        }
        if ("Percent".equals(type)) {
            return renderJavaExpr(node.operand()) + ".divide(new BigDecimal(\"100\"), MC)";
        }
        if ("Function".equals(type)) {
            String name = node.name().toLowerCase();
            if ("sqrt".equals(name) && node.args() != null && !node.args().isEmpty()) {
                return renderJavaExpr(node.args().get(0)) + ".sqrt(MC)";
            }
            if ("abs".equals(name) && node.args() != null && !node.args().isEmpty()) {
                return renderJavaExpr(node.args().get(0)) + ".abs(MC)";
            }
            if (node.args() != null && !node.args().isEmpty()) {
                return renderJavaExpr(node.args().get(0));
            }
        }
        return "BigDecimal.ZERO";
    }

    private String renderRustExpr(AstBlueprintNode node) {
        if (node == null) return "BigInt::zero()";
        String type = node.type();
        if ("Literal".equals(type)) {
            String val = node.value();
            if (val.contains(".")) {
                val = val.substring(0, val.indexOf('.'));
            }
            return "BigInt::from(" + (val.isEmpty() ? "0" : val) + "i64)";
        }
        if ("Variable".equals(type)) {
            return sanitizeIdentifier(node.name());
        }
        if ("Operator".equals(type)) {
            String left = renderRustExpr(node.left());
            String right = renderRustExpr(node.right());
            String op = node.op();
            if ("^".equals(op)) {
                return "num_traits::pow(" + left + ", (" + right + ").to_u32().unwrap_or(0) as usize)";
            }
            return "(" + left + " " + op + " " + right + ")";
        }
        if ("Unary".equals(type)) {
            return "(-" + renderRustExpr(node.operand()) + ")";
        }
        if ("Percent".equals(type)) {
            return "(" + renderRustExpr(node.operand()) + " / BigInt::from(100i64))";
        }
        if ("Function".equals(type)) {
            String name = node.name().toLowerCase();
            if ("abs".equals(name) && node.args() != null && !node.args().isEmpty()) {
                return "(" + renderRustExpr(node.args().get(0)) + ").abs()";
            }
            if (node.args() != null && !node.args().isEmpty()) {
                return renderRustExpr(node.args().get(0));
            }
        }
        return "BigInt::zero()";
    }

    private void collectVariables(AstBlueprintNode node, Set<String> vars) {
        if (node == null) return;
        if ("Variable".equals(node.type()) && node.name() != null) {
            vars.add(node.name());
        }
        if (node.left() != null) collectVariables(node.left(), vars);
        if (node.right() != null) collectVariables(node.right(), vars);
        if (node.operand() != null) collectVariables(node.operand(), vars);
        if (node.args() != null) {
            for (AstBlueprintNode arg : node.args()) {
                collectVariables(arg, vars);
            }
        }
    }

    private String sanitizeIdentifier(String raw) {
        if (raw == null || raw.isBlank()) return "var";
        String clean = raw.replaceAll("[^a-zA-Z0-9_]", "_");
        if (Character.isDigit(clean.charAt(0))) {
            clean = "_" + clean;
        }
        return clean;
    }
}
