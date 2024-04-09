package pt.up.fe.comp2024.ast;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;

public class TypeUtils {

    private static final String INT_TYPE_NAME = "int";
    private static final String BOOLEAN_TYPE_NAME = "boolean";

    public static String getIntTypeName() {
        return INT_TYPE_NAME;
    }

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
            case ARRAY_ACCESS_OP -> new Type(INT_TYPE_NAME, false);
            default -> throw new UnsupportedOperationException("Can't compute type for expression kind '" + kind + "'");
        };

        return type;
    }

    private static Type getBinExprType(JmmNode binaryExpr) {
        String operator = binaryExpr.get("op");

        return switch (operator) {
            case "+", "*", "-", "/", "<" -> new Type(INT_TYPE_NAME, false);
            case "&&" -> new Type(BOOLEAN_TYPE_NAME, false);
            default ->
                    throw new RuntimeException("Unknown operator '" + operator + "' of expression '" + binaryExpr + "'");
        };
    }


    private static Type getIdentifierType(JmmNode idExpr, SymbolTable table, String currentMethod) {
        var id = idExpr.get("ID");

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

        throw new RuntimeException("Variable '" + id + "' does not exist.");
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
