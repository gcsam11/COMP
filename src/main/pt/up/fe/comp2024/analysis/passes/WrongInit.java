package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Checks if an assignment is correct.
 */
public class WrongInit extends AnalysisVisitor {

    private String currentMethod;
    @Override
    public void buildVisitor() {
        addVisit(Kind.CLASS_DECL, this::visitClassDecl);
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.ASSIGN_STMT, this::visitAssignStmt);
        addVisit(Kind.VAR_DECL, this::visitVarDecl);
        addVisit(Kind.LENGTH_OP, this::visitLengthOp);
    }

    // check duplicates
    private Void visitClassDecl(JmmNode classDecl, SymbolTable table) {
        int i = 0;
        for(var method : table.getMethods()){
            if(table.getMethods().subList(i + 1, table.getMethods().size()).contains(method)){
                var message = String.format("Method '%s' is duplicated", method);
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(classDecl),
                        NodeUtils.getColumn(classDecl),
                        message,
                        null)
                );
            }
            i++;
        }

        i = 0;
        for(var field : table.getFields()){
            if(table.getFields().subList(i + 1, table.getFields().size()).contains(field)){
                var message = String.format("Field '%s' is duplicated", field.getName());
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(classDecl),
                        NodeUtils.getColumn(classDecl),
                        message,
                        null)
                );
            }
            i++;
        }

        i = 0;
        // strip dots from import names and get the last part of the name
        for(var var : table.getImports()){
            if(table.getImports().subList(i + 1, table.getImports().size()).contains(var)){
                var message = String.format("Import '%s' is duplicated", var);
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(classDecl),
                        NodeUtils.getColumn(classDecl),
                        message,
                        null)
                );
            }
            i++;
        }

        return null;
    }

    // check if method name is MAIN, and it's declaration is correct
    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");

        int i = 0;
        for(var param : table.getParameters(currentMethod)){
            if(table.getParameters(currentMethod).subList(i + 1, table.getParameters(currentMethod).size()).contains(param)){
                var message = String.format("Parameter '%s' is duplicated", param.getName());
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(method),
                        NodeUtils.getColumn(method),
                        message,
                        null)
                );
            }
            i++;
        }

        i = 0;
        for(var var : table.getLocalVariables(currentMethod)){
            if(table.getLocalVariables(currentMethod).subList(i + 1, table.getLocalVariables(currentMethod).size()).contains(var)){
                var message = String.format("Variable '%s' is duplicated", var.getName());
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(method),
                        NodeUtils.getColumn(method),
                        message,
                        null)
                );
            }
            i++;
        }

        if(currentMethod.equals("main")){
            var parameters = method.getChildren(Kind.PARAM_DECL);
            if(parameters.size() != 1){
                var message = String.format("Main method should have 1 parameter, found %d", parameters.size());
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(method),
                        NodeUtils.getColumn(method),
                        message,
                        null)
                );
            }
            else {
                if(method.getChildren(Kind.PARAM_DECL).get(0).getChildren(Kind.STRING_ARRAY_TYPE).size() != 1){
                    var message = String.format("Main method parameter should be of type '%s[]', found '%s'", TypeUtils.getStringTypeName(), TypeUtils.getExprType(method.getChildren(Kind.PARAM_DECL).get(0), table, currentMethod));
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            NodeUtils.getLine(method),
                            NodeUtils.getColumn(method.getChildren(Kind.PARAM_DECL).get(0)),
                            message,
                            null)
                    );
                }
            }
            if(!Boolean.parseBoolean(method.get("isStatic"))){
                var message = "Main method should be static";
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(method),
                        NodeUtils.getColumn(method),
                        message,
                        null)
                );
            }
            if(!Objects.equals(method.getChild(0).getKind(), Kind.VOID_TYPE.getNodeName())){
                var message = String.format("Main method return type should be '%s'", TypeUtils.getVoidTypeName());
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(method),
                        NodeUtils.getColumn(method.getChild(0)),
                        message,
                        null)
                );
            }
        }
        return null;
    }

    private Void visitLengthOp(JmmNode lengthOp, SymbolTable table) {
        if(!lengthOp.get("value").equals("length")){
            var message = "Length Operator does not contain keyword 'length'";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(lengthOp),
                    NodeUtils.getColumn(lengthOp),
                    message,
                    null)
            );
        }

        try {
            Type exprType = TypeUtils.getExprType(lengthOp.getChild(0), table, currentMethod);

            if(!exprType.isArray()){
                var message = String.format("'%s' is not an array", exprType);
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(lengthOp),
                        NodeUtils.getColumn(lengthOp.getChild(0)),
                        message,
                        null)
                );
            }
        } catch (RuntimeException e) {
            var message = String.format("'%s'", e.getMessage());
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(lengthOp),
                    NodeUtils.getColumn(lengthOp),
                    message,
                    null)
            );
        }

        return null;
    }

    private Void visitAssignStmt(JmmNode assignStmt, SymbolTable table) {
        SpecsCheck.checkNotNull(currentMethod, () -> "Expected current method to be set");

        if(!assignStmt.getChild(0).getKind().equals(Kind.IDENTIFIER.getNodeName())
                && !assignStmt.getChild(0).getKind().equals(Kind.ARRAY_ACCESS_OP.getNodeName())){
            if(assignStmt.getChild(0).getKind().equals(Kind.ARRAY_ACCESS_OP.getNodeName())){
                if(assignStmt.getChild(0).getChildren(Kind.IDENTIFIER).size() == 0){
                    var message = "Left side of assignment statement should be an identifier";
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            NodeUtils.getLine(assignStmt),
                            NodeUtils.getColumn(assignStmt),
                            message,
                            null)
                    );
                }
            }
            var message = "Left side of assignment statement should be an identifier";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(assignStmt),
                    NodeUtils.getColumn(assignStmt),
                    message,
                    null)
            );
        }

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

    private Void visitVarDecl(JmmNode varDeclaration, SymbolTable table){
        try{
            var type = TypeUtils.getExprType(varDeclaration, table, currentMethod);
        }
        catch (RuntimeException e) {
            var message = String.format("'%s'", e.getMessage());
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(varDeclaration),
                    NodeUtils.getColumn(varDeclaration),
                    message,
                    null)
            );
        }
        return null;
    }

    private Boolean checkImportsAndExtensions(Type idType, Type assignType, SymbolTable table, JmmNode assignStmt) {
        // check if the assign as a member access descendant, if it does assume the return type of the function is correct (Import checked in another file)
        if(assignStmt.getChildren(Kind.MEMBER_ACCESS_OP).size() > 0){
            return true;
        }
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

