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
import pt.up.fe.specs.util.threadstream.ObjectStream;

import java.util.Objects;

/**
 * Checks if an assignment is correct.
 */
public class WrongInit extends AnalysisVisitor {

    private String currentMethod;
    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.ASSIGN_STMT, this::visitAssignStmt);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }

    private Void visitAssignStmt(JmmNode assignStmt, SymbolTable table) {
        SpecsCheck.checkNotNull(currentMethod, () -> "Expected current method to be set");

        try {
            Type idType = TypeUtils.getExprType(assignStmt.getChild(0), table, currentMethod);
            Type assignType = TypeUtils.getExprType(assignStmt.getChild(1), table, currentMethod);

            if(!checkImportsAndExtensions(idType, assignType, table, assignStmt)){
                var message = String.format("'%s' can not be assigned to '%s'", assignType, idType);
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(assignStmt),
                        NodeUtils.getColumn(assignStmt),
                        message,
                        null)
                );
            }

        } catch (RuntimeException e) {
            var message = String.format("'%s'", e.getMessage());
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(assignStmt),
                    NodeUtils.getColumn(assignStmt),
                    message,
                    null)
            );
        }

        return null;
    }

    private Boolean checkImportsAndExtensions(Type idType, Type assignType, SymbolTable table, JmmNode assignStmt) {
        if(Objects.equals(idType, assignType) &&
                (checkIfTypeIsPrimitive(idType) || checkIfTypeIsImported(idType, table) || table.getClassName().equals(idType.getName()))){
            return true;
        }
        else {
            if(checkIfTypeIsImported(idType, table) && checkIfTypeIsImported(assignType, table)){
                return true;
            }
            else if(checkIfTypeIsExtension(idType, table) && table.getClassName().equals(assignType.getName())){
                return true;
            }
        }

        return false;
    }

    private Boolean checkIfTypeIsPrimitive(Type type){
        return (TypeUtils.getIntTypeName().equals(type.getName()) || TypeUtils.getBooleanTypeName().equals(type.getName()));
    }

    private Boolean checkIfTypeIsImported(Type type, SymbolTable table){
        return table.getImports().contains(type.getName());
    }

    private Boolean checkIfTypeIsExtension(Type type, SymbolTable table){
        return table.getImports().contains(table.getSuper()) && Objects.equals(table.getSuper(), type.getName());
    }
}

