package pt.up.fe.comp2024.optimization_jasmin;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.specs.util.SpecsCheck;
import pt.up.fe.specs.util.utilities.StringLines;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class JasminGeneratorVisitor extends AJmmVisitor<Void, String> {

    private static final String NL = "\n";
    private static final String TAB = "   ";

    private final SymbolTable table;

    private JasminExprGeneratorVisitor exprGenerator;

    private String currentMethod;
    private int nextRegister;

    private Map<String, Integer> currentRegisters;

    public JasminGeneratorVisitor(SymbolTable table) {
        this.table = table;
        this.exprGenerator = null;
        currentMethod = null;
        nextRegister = -1;
        currentRegisters = null;
    }


    @Override
    protected void buildVisitor() {
        // Using strings to avoid compilation problems in projects that
        // might no longer have the equivalent enums in Kind class.
        addVisit("Program", this::visitProgram);
        addVisit("ClassDecl", this::visitClassDecl);
        addVisit("MethodDecl", this::visitMethodDecl);
        addVisit("NewOpObject", this::visitNewOpObject);
        addVisit("AssignStmt", this::visitAssignStmt);
        addVisit("ReturnStmt", this::visitReturnStmt);
        addVisit("ExprStmt", this::visitExprStmt);
        addVisit("IfElseStmt", this::visitIfElseStmt);
    }


    private String visitProgram(JmmNode program, Void unused) {

        // Get class decl node
        var classDecl = program.getChildren(Kind.CLASS_DECL).get(0);
        SpecsCheck.checkArgument(classDecl.isInstance("ClassDecl"), () -> "Expected a node of type 'ClassDecl', but instead got '" + classDecl.getKind() + "'");

        return visit(classDecl);
    }

    private String visitClassDecl(JmmNode classDecl, Void unused) {
        var code = new StringBuilder();

        // generate class name
        var className = table.getClassName();
        code.append(".class ").append(className).append(NL);
        var superClass = "empty";

        // generate super class if it exists
        if(!Objects.equals(table.getSuper(), null)){
            var program = classDecl.getParent();
            var classes = "";
            for(var imports: program.getChildren(Kind.IMPORT_DECL.getNodeName())){
                if(imports.get("importName").contains(table.getSuper())){
                    var importName = imports.get("importName");
                    classes = importName
                            .replace("[", "")
                            .replace("]", "")
                            .replace(" ", "")
                            .replace(",","/");
                    break;
                }
            }
            code.append(".super ").append(classes).append(NL);
            superClass = "invokespecial " + classes +"/<init>()V\n";
        }
        else{
            code.append(".super java/lang/Object").append(NL);
            superClass = "invokespecial java/lang/Object/<init>()V\n";
        }

        // TODO - Generate class fields
        for(var field : table.getFields()) {
            var fieldType = field.getType();
            var fieldName = field.getName();
            var auxfield = "empty";
            switch (fieldType.getName()) {
                case "int":
                    auxfield = "I";
                    break;
                case "boolean":
                    auxfield = "Z";
                    break;
                default:
                    if(table.getImports().contains(fieldType.getName()))
                        auxfield = getImport(classDecl, fieldType);
                        auxfield = "L"+auxfield+";";
                    break;
            }
            code.append(".field public "+fieldName+" "+auxfield).append(NL);

        }

        // generate a single constructor method
        var defaultConstructor = """
                ;default constructor
                .method public <init>()V
                    aload_0    
                \t"""+superClass+"""
                \treturn
                .end method
                """;
        code.append(defaultConstructor);

        // generate code for all other methods
        for (var method : classDecl.getChildren("MethodDecl")) {
            code.append(visit(method));
        }

        return code.toString();
    }

    private String visitMethodDecl(JmmNode methodDecl, Void unused) {
        var methodName = methodDecl.get("name");

        // set method
        currentMethod = methodName;

        // set next register that can be used
        // if method is static, then can start at 0
        // if method is not static, 0 contains 'this', and must start at 1
        // for the initial language, there are no static methods
        nextRegister = methodDecl.getObject("isStatic", Boolean.class) ? 0 : 1;

        // initialize register map and set parameters
        currentRegisters = new HashMap<>();
        for (var param : methodDecl.getChildren("ParamDecl")) {
            currentRegisters.put(param.get("var"), nextRegister);
            nextRegister++;
        }

        exprGenerator = new JasminExprGeneratorVisitor(currentRegisters, table, currentMethod);

        var code = new StringBuilder();

        // calculate modifier
        var modifier = methodDecl.getObject("isPublic", Boolean.class) ? "public " : "";

        // TODO: Hardcoded param types and return type, needs to be expanded -> DONE
        if(methodDecl.getObject("isStatic",Boolean.class))
            code.append("\n.method ").append(modifier).append("static ").append(methodName).append("(");
        else
            code.append("\n.method ").append(modifier).append(methodName).append("(");

        String classes = "empty";
        for(var param : methodDecl.getChildren("ParamDecl")){
            switch(param.getChild(0).getKind()){
                case "IntType":
                    code.append("I");
                    break;
                case "BooleanType":
                    code.append("Z");
                    break;
                case "StringArrayType":
                    code.append("[Ljava/lang/String;");
                    break;
                default:
                    var importNode = methodDecl.getParent();

                    while(!importNode.getKind().equals("Program")) {
                        importNode = importNode.getParent();
                    }

                    var importNodes = importNode.getChildren();
                    for(var anImport: importNodes) {
                        classes = anImport.get("importName")
                                .replace("[", "")
                                .replace("]", "")
                                .replace(" ", "")
                                .replace(",","/");

                        var auxLast = classes.split("/");

                        if(Objects.equals(auxLast[auxLast.length - 1],param.getChild(0).get("typeName"))) {
                            code.append("L").append(classes).append(";");
                            break;
                        }
                    }
                    break;
            }
        }

        var returnType = table.getReturnType(methodName);

        switch(returnType.getName()){
            case "int":
                if(returnType.isArray()) code.append(")[I").append(NL);
                else code.append(")I").append(NL);
                break;
            case "boolean":
                if(returnType.isArray()) code.append(")[Z").append(NL);
                else code.append(")Z").append(NL);
                break;
            case "void":
                code.append(")V").append(NL);
                break;
            default:
                if(table.getImports().contains(returnType.getName())){
                    var auxclasses = getImport(methodDecl, returnType);
                    code.append(")L").append(auxclasses).append(";").append(NL);
                }
                break;
        }

        // Add limits
        code.append(TAB).append(".limit stack 99").append(NL);
        code.append(TAB).append(".limit locals 99").append(NL);

        if (!methodDecl.getObject("isStatic", Boolean.class)) { // if it has a "This" Node, only call aload_0 when This is called
            boolean thisExists = false;
            for(JmmNode descendant: methodDecl.getDescendants()) {
                if(descendant.getKind().equals("This")) {
                    thisExists = true;
                    break;
                }
            }
            if(!thisExists) code.append(TAB).append("aload_0").append(NL);
        }

        for (var stmt : methodDecl.getChildren("Stmt")) {
            // Get code for statement, split into lines and insert the necessary indentation
            var instCode = StringLines.getLines(visit(stmt)).stream()
                    .collect(Collectors.joining(NL + TAB, TAB, NL));

            code.append(instCode);
            if(stmt.getKind().equals("IfElseStmt")) { //Label for code to jump into after if statement
                code.append(createReverseLabelName(stmt)).append(":").append(NL);
            }
        }

        if(returnType.getName().equals("void"))
            code.append("\treturn").append(NL);

        code.append(".end method\n");

        // reset information
        exprGenerator = null;
        nextRegister = -1;
        currentRegisters = null;
        currentMethod = null;

        return code.toString();
    }

    private String createReverseLabelName(JmmNode ifElseStmt) {
        return "label_" + ifElseStmt.get("lineEnd") +
                ifElseStmt.get("colStart") +
                ifElseStmt.get("lineStart") +
                ifElseStmt.get("colEnd");
    }

    private String visitNewOpObject(JmmNode newOp, Void unused) {
        var code = new StringBuilder();

        var lhs = newOp.getChild(0);
        SpecsCheck.checkArgument(lhs.isInstance("NewOp"), () -> "Expected a node of type 'NewOp', but instead got '" + lhs.getKind() + "'");

        return code.toString();
    }

    private String visitExprStmt(JmmNode exprStmt, Void unused) {
        var code = new StringBuilder();
        var stmt = exprStmt.getChild(0);

        if (stmt.getKind().equals("MemberAccessOp")) {
            // store value in top of the stack in destination
            var lhs = stmt.getChild(0);
            SpecsCheck.checkArgument(lhs.isInstance("Identifier") || lhs.isInstance("This"), () -> "Expected a node of type 'Identifier', but instead got '" + lhs.getKind() + "'");

            var destName = lhs.get("value");

            // get register
            var reg = currentRegisters.get(destName);

            // If no mapping, variable has not been assigned yet, create mapping
            if (reg == null) {
                reg = nextRegister;
                currentRegisters.put(destName, reg);
                nextRegister++;
            }

            exprGenerator.visit(stmt, code);
        }

        return code.toString();
    }

    private String visitAssignStmt(JmmNode assignStmt, Void unused) {
        var code = new StringBuilder();

        // store value in top of the stack in destination
        var lhs = assignStmt.getChild(0);
        SpecsCheck.checkArgument(lhs.isInstance("Identifier") || lhs.isInstance("This"), () -> "Expected a node of type 'Identifier', but instead got '" + lhs.getKind() + "'");

        var destName = lhs.get("value");

        // get register
        var reg = currentRegisters.get(destName);

        // If no mapping, variable has not been assigned yet, create mapping
        if (reg == null) {
            reg = nextRegister;
            currentRegisters.put(destName, reg);
            nextRegister++;
        }

        // generate code that will put the value on the right on top of the stack
        exprGenerator.visit(assignStmt.getChild(1), code);

        boolean isField = false;
        var fieldType = "empty";
        for(var field : table.getFields()) {
            if (field.getName().equals(destName)) {
                isField = true;
                fieldType = field.getType().getName();
                break;
            }
        }
        if(table.getParameters(currentMethod).stream().anyMatch(param -> param.getName().equals(destName)) ||
                table.getLocalVariables(currentMethod).stream().anyMatch(varDecl -> varDecl.getName().equals(destName))) {
            isField = false;
        }

        if(fieldType.equals("int"))
            fieldType = "I";
        else if(fieldType.equals("boolean"))
            fieldType = "Z";

        String literalType = assignStmt.getChild(1).getKind();
        switch (literalType) {
            case "This":
                code.append("invokespecial ").append(table.getClassName()).append("/<init>()V").append(NL);
            case "NewOpObject":
                code.append("astore ").append(reg).append(NL);
                break;
            case "IntegerLiteral", "BooleanLiteral":
                if(isField)
                    code.append("putfield ").append(table.getClassName()).append("/").append(destName).append(" ").append(fieldType).append(NL);
                else
                    code.append("istore ").append(reg).append(NL);
                break;
        }

        return code.toString();
    }

    private String visitReturnStmt(JmmNode returnStmt, Void unused) {

        var code = new StringBuilder();

        // generate code that will put the value of the return on the top of the stack
        exprGenerator.visit(returnStmt.getChild(0), code);
        var returnType = table.getReturnType(returnStmt.getAncestor("MethodDecl").get().get("name"));

        switch(returnType.getName()){
            case "int", "boolean":
                code.append(NL);
                code.append("ireturn").append(NL);
                break;
            case "void":
                code.append("return").append(NL);
                break;
            default:
                code.append("areturn");
        }

        return code.toString();
    }

    private String getImport(JmmNode node, Type type){
        var auxclasses = "";
        var program = node.getParent();
        while(!program.getKind().equals("Program")) {
            program = program.getParent();
        }
        for(var imports: program.getChildren(Kind.IMPORT_DECL.getNodeName())){
            if(imports.get("importName").contains(type.getName())){
                var importName = imports.get("importName");
                auxclasses = importName
                        .replace("[", "")
                        .replace("]", "")
                        .replace(" ", "")
                        .replace(",","/");
                break;
                }
            }
        return auxclasses;
    }

    private String visitIfElseStmt(JmmNode ifElseStmt, Void unused) {
        var code = new StringBuilder();

        String labelName = createLabelName(ifElseStmt);
        JmmNode masterLbl = ifElseStmt;
        while(!masterLbl.getParent().getKind().equals("MethodDecl")) {
            masterLbl = masterLbl.getParent();
        }
        String goToLabelName = createReverseLabelName(masterLbl);

        // add compare instruction
        var comp = ifElseStmt.getChild(0);
        if(comp.getKind().equals("BinaryExpr")) {
            exprGenerator.visit(ifElseStmt.getChild(0), code);
            code.append(labelName).append(NL);
        } else if(comp.getKind().equals("BooleanLiteral")) {
            exprGenerator.visit(comp, code);
            code.append("ifne ").append(labelName).append(NL);
        }

        var falseBlockStmt = ifElseStmt.getChild(2);
        var trueBlockStmt = ifElseStmt.getChild(1);

        dealWithStatements(falseBlockStmt, code, goToLabelName, false);
        dealWithStatements(trueBlockStmt, code, "", true);

        return code.toString();
    }

    private void dealWithStatements(JmmNode trueOrFalseStmt, StringBuilder code, String labelName, boolean stmt) {
        //if(trueOrFalseStmt.getKind().equals("BinaryExpr")) exprGenerator.visit(trueOrFalseStmt.getChild(0), code);

        if(!stmt) {
            dealWithStatementsHelper(trueOrFalseStmt, code);
            if(!trueOrFalseStmt.getChild(0).getKind().equals("IfElseStmt") || trueOrFalseStmt.getParent().getKind().equals("IfElseStmt"))
                code.append("goto ").append(labelName).append(NL);
            else code.append(createReverseLabelName(trueOrFalseStmt.getParent())).append(NL);
        } else {
            code.append(createLabelName(trueOrFalseStmt.getParent())).append(": ").append(NL);
            dealWithStatementsHelper(trueOrFalseStmt, code);
        }
    }

    private void dealWithStatementsHelper(JmmNode trueOrFalseStmt, StringBuilder code) {
        if(trueOrFalseStmt.getKind().equals("BlockStmt")) {
            for(JmmNode exprStmt : trueOrFalseStmt.getChildren()) {
                if(exprStmt.getKind().equals("IfElseStmt"))
                    code.append(visit(exprStmt));
                else
                    exprGenerator.visit(exprStmt.getChild(0), code);
            }
        } else if(trueOrFalseStmt.getKind().equals("ExprStmt")) {
            exprGenerator.visit(trueOrFalseStmt.getChild(0), code);
        } else if(trueOrFalseStmt.getKind().equals("IfElseStmt")) {
            code.append(visit(trueOrFalseStmt));
        }
    }

    private String createLabelName(JmmNode ifElseStmt) {
        return "label_" + ifElseStmt.get("colEnd") +
                ifElseStmt.get("lineStart") +
                ifElseStmt.get("colStart") +
                ifElseStmt.get("lineEnd");
    }
}