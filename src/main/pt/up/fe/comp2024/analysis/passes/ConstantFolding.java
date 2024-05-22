package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.specs.util.SpecsCheck;

/**
 * Constant folding analysis.
 */
public class ConstantFolding extends AnalysisVisitor {

    private String currentMethod;

    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.BINARY_EXPR, this::visitBinaryExpr);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }

    private Void visitBinaryExpr(JmmNode binaryExpr, SymbolTable table) {
        SpecsCheck.checkNotNull(currentMethod, () -> "Expected current method to be set");

        var left = binaryExpr.getChild(0);
        var right = binaryExpr.getChild(1);

        if(left.getKind().equals("IntegerLiteral") && right.getKind().equals("IntegerLiteral")) {
            var leftValue = Integer.parseInt(left.get("value"));
            var rightValue = Integer.parseInt(right.get("value"));

            var operator = binaryExpr.get("op");

            var result = 0;
            switch(operator) {
                case "+":
                    result = leftValue + rightValue;
                    break;
                case "-":
                    result = leftValue - rightValue;
                    break;
                case "*":
                    result = leftValue * rightValue;
                    break;
                case "/":
                    result = leftValue / rightValue;
                    break;
                default:
                    return null;
            }

            binaryExpr.put("value", Integer.toString(result));
        }


        return null;
    }
}