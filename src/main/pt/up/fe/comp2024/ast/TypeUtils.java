package pt.up.fe.comp2024.ast;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import java.util.Objects;

public class TypeUtils {

    private static final String INT_TYPE_NAME = "int";
    private static final String BOOLEAN_TYPE_NAME = "boolean";

    public static String getIntTypeName() {
        return INT_TYPE_NAME;
    }
    public static String getBooleanTypeName() { return BOOLEAN_TYPE_NAME; }

    /**
     * Gets the {@link Type} of an arbitrary expression.
     *
     * @param expr
     * @param table
     * @param currentMethod
     * @return
     */
    public static Type getExprType(JmmNode expr, SymbolTable table, String currentMethod) {
        // TODO: Simple implementation that needs to be expanded

        var kind = Kind.fromString(expr.getKind());

        Type type = switch (kind) {
            case BINARY_EXPR -> getBinExprType(expr);
            case IDENTIFIER -> getIdentifierType(expr, table, currentMethod);
            case INTEGER_LITERAL -> new Type(INT_TYPE_NAME, false);
            case BOOLEAN_LITERAL -> new Type(BOOLEAN_TYPE_NAME, false);
            case ARRAY_ACCESS_OP -> new Type(INT_TYPE_NAME, false);
            case ARRAY_CREATION_OP -> getArrayType(expr, table, currentMethod);
            case NEW_OP -> getNewOpType(expr, table, currentMethod);
            case MEMBER_ACCESS_OP -> getMemberAccessType(expr, table, currentMethod);
            case THIS -> new Type(table.getClassName(), false);
            default -> throw new UnsupportedOperationException("Can't compute type for expression kind '" + kind + "'");
        };

        return type;
    }

    private static Type getBinExprType(JmmNode binaryExpr) {
        String operator = binaryExpr.get("op");

        return switch (operator) {
            case "+", "*", "-", "/" -> new Type(INT_TYPE_NAME, false);
            case "&&", "<" -> new Type(BOOLEAN_TYPE_NAME, false);
            default ->
                    throw new RuntimeException("Unknown operator '" + operator + "' of expression '" + binaryExpr + "'");
        };
    }


    private static Type getIdentifierType(JmmNode idExpr, SymbolTable table, String currentMethod) {
        var id = idExpr.get("value");

        // Var is an import, return
        if(table.getImports().stream()
                .anyMatch(importDecl -> importDecl.equals(id))) {
            return new Type(id, false);
        }

        // Var is a field, return
        if (table.getFields().stream()
                .anyMatch(param -> param.getName().equals(id))) {
            return table.getFields().stream()
                    .filter(param -> param.getName().equals(id))
                    .findFirst()
                    .get()
                    .getType();
        }

        // Var is a parameter, return
        if (table.getParameters(currentMethod).stream()
                .anyMatch(param -> param.getName().equals(id))) {
            return table.getParameters(currentMethod).stream()
                    .filter(param -> param.getName().equals(id))
                    .findFirst()
                    .get()
                    .getType();
        }

        // Var is a declared variable, return
        if (table.getLocalVariables(currentMethod).stream()
                .anyMatch(varDecl -> varDecl.getName().equals(id))) {
            return table.getLocalVariables(currentMethod).stream()
                    .filter(varDecl -> varDecl.getName().equals(id))
                    .findFirst()
                    .get()
                    .getType();
        }

        throw new RuntimeException("Undeclared symbol '" + id + "'.");
    }

    private static Type getArrayType(JmmNode arrayCreationOp, SymbolTable table, String currentMethod) {
        // Get the type of the first element in the array
        Type firstElementType = TypeUtils.getExprType(arrayCreationOp.getChild(0), table, currentMethod);

        // Iterate over the elements in the array
        for (JmmNode element : arrayCreationOp.getChildren()) {
            Type elementType = TypeUtils.getExprType(element, table, currentMethod);

            // Check if the type of the element matches the type of the array
            if (!elementType.getName().equals(firstElementType.getName())) {
                throw new RuntimeException("Array elements must have the same type.");
            }
        }

        return new Type(firstElementType.getName(), true);
    }

    private static Type getNewOpType(JmmNode newOp, SymbolTable table, String currentMethod) {
        // Check if there exists a child
        if (newOp.getNumChildren() == 0) {
            return new Type(newOp.get("value"), false);
        }

        return new Type(INT_TYPE_NAME, true);
    }

    private static Type getMemberAccessType(JmmNode memberAccess, SymbolTable table, String currentMethod){
        var methodExists = table.getMethods().contains(memberAccess.get("value"));
        if (methodExists) { // If the method is declared then method does not come from an import or super class
            return table.getReturnType(memberAccess.get("value"));
        }
        else {
            return getExprType(memberAccess.getChild(0), table, currentMethod);
        }
    }

    /**
     * @param sourceType
     * @param destinationType
     * @return true if sourceType can be assigned to destinationType
     */
    public static boolean areTypesAssignable(Type sourceType, Type destinationType) {
        // TODO: Simple implementation that needs to be expanded
        return sourceType.getName().equals(destinationType.getName());
    }
}
