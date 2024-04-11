package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;
import pt.up.fe.specs.util.SpecsCheck;
import java.util.Objects;

/**
 * Checks if a member access is correct and all the imported classes logistic.
 */
public class ImportDeclAndMemberAccess extends AnalysisVisitor {

    private String currentMethod;
    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.MEMBER_ACCESS_OP, this::visitMemberAccessOp);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }

    private Void visitMemberAccessOp(JmmNode memberAccess, SymbolTable table) {
        SpecsCheck.checkNotNull(currentMethod, () -> "Expected current method to be set");

        try{
            var methodType = TypeUtils.getExprType(memberAccess, table, currentMethod);
            var methodName = memberAccess.get("value");

            if (!table.getImports().isEmpty()) { // There exists imported classes
                if (table.getImports().contains(table.getSuper())) { // Super class is imported
                    if (methodType.getName().equals(table.getClassName()) || methodType.getName().equals(table.getSuper())) { // Accessing a member of the class or the super class
                        return null;
                    }
                } else if (table.getImports().contains(methodType.getName())) { // Accessing a member of an imported class
                    return null;
                } else {
                    var message = String.format("Accessing member '%s' of a class that is not imported.", methodType.getName());
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            NodeUtils.getLine(memberAccess),
                            NodeUtils.getColumn(memberAccess),
                            message,
                            null)
                    );
                }

                return null;
            }

            if(methodType.getName().equals(table.getClassName())){
                var message = String.format("Call to undeclared method '%s'", methodName);
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(memberAccess),
                        NodeUtils.getColumn(memberAccess),
                        message,
                        null)
                );

                return null;
            }

            if(memberAccess.getChildren().size() > 1) { // member has arguments
                int i = 1;
                for( JmmNode arg: memberAccess.getChildren().subList(1, memberAccess.getChildren().size())) {
                    var idType = TypeUtils.getExprType(arg, table, currentMethod);
                    var argType = table.getParameters(methodName).get(i-1).getType();
                    if (!Objects.equals(idType, argType)) {
                        var message = String.format("Assigning '%s' to '%s' in method '%s' call.", idType, argType, methodName);
                        addReport(Report.newError(
                                Stage.SEMANTIC,
                                NodeUtils.getLine(memberAccess),
                                NodeUtils.getColumn(memberAccess),
                                message,
                                null)
                        );
                    }
                    i++;
                }
            }

        } catch (RuntimeException e) {
            // Do Nothing
        }
        return null;
    }
}