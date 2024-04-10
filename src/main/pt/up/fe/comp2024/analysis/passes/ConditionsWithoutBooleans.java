package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;
import pt.up.fe.specs.util.SpecsCheck;

/**
 * Checks if the condition in a While/IfElse statement is a boolean.
 */
public class ConditionsWithoutBooleans extends AnalysisVisitor {

    private String currentMethod;

    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.WHILE_STMT, this::visitWhileStmt);
        addVisit(Kind.IF_ELSE_STMT, this::visitIfElseStmt);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }

    private Void visitWhileStmt(JmmNode whileStmt, SymbolTable table) {
        SpecsCheck.checkNotNull(currentMethod, () -> "Expected current method to be set");

        Type conditionType = TypeUtils.getExprType(whileStmt.getChild(0), table, currentMethod);

        if (!conditionType.getName().equals(TypeUtils.getBooleanTypeName())) {
            var message = String.format("Condition in while statement is not a boolean");
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(whileStmt),
                    NodeUtils.getColumn(whileStmt),
                    message,
                    null)
            );
        }

        return null;
    }

    private Void visitIfElseStmt(JmmNode IfElseStmt, SymbolTable table){
        SpecsCheck.checkNotNull(currentMethod, () -> "Expected current method to be set");

        Type conditionType = TypeUtils.getExprType(IfElseStmt.getChild(0), table, currentMethod);

        if (!conditionType.getName().equals(TypeUtils.getBooleanTypeName())) {
            var message = String.format("Condition in If statement is not a boolean");
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(IfElseStmt),
                    NodeUtils.getColumn(IfElseStmt),
                    message,
                    null)
            );
        }

        return null;
    }
}