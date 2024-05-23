package pt.up.fe.comp2024.optimization_jasmin;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.TypeUtils;
import pt.up.fe.specs.util.SpecsCheck;
import pt.up.fe.specs.util.utilities.StringLines;

import java.util.*;
import java.util.stream.Collectors;

public class JasminGeneratorVisitor extends AJmmVisitor<Void, String> {

    private static final String NL = "\n";
    private static final String TAB = "   ";

    private final SymbolTable table;

    private JasminExprGeneratorVisitor exprGenerator;

    private String currentMethod;
    private int nextRegister;
    private int currNumInStack;
    private int maxInStack;

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
    }

    private void updateRegisters(JmmNode AssignStmt) {
        var methodDecl = AssignStmt.getAncestor("MethodDecl").get();
        var noDuplicateRegisterNumbers = new HashMap<String, Integer>();

        // check for duplicate registers
        for (var entry : currentRegisters.entrySet()) {
            if (!entry.getKey().equals("temp_" + entry.getValue()) && methodDecl.getChildren("ParamDecl").stream().anyMatch(param -> param.get("var").equals(entry.getKey()))) {
                noDuplicateRegisterNumbers.put(entry.getKey(), entry.getValue());
            } else {
                if (entry.getKey().equals("temp_" + entry.getValue())) {
                    noDuplicateRegisterNumbers.put("temp_" + entry.getValue(), entry.getValue());
                }
            }
        }

        String previousEntryName = "";
        Integer previousEntryValue = -1;

        // recheck for duplicate registers of non ParamDecl variables
        for(var registers: currentRegisters.entrySet()){
            if(!previousEntryName.equals(registers.getKey()) && previousEntryValue == registers.getValue() && !previousEntryName.equals("temp_" + previousEntryValue)){
                noDuplicateRegisterNumbers.put(previousEntryName, noDuplicateRegisterNumbers.size() + 1);
            }
            previousEntryName = registers.getKey();
            previousEntryValue = registers.getValue();
        }

        currentRegisters = noDuplicateRegisterNumbers;
        nextRegister = currentRegisters.size();
        exprGenerator.setRegisters(currentRegisters, nextRegister);
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

        for(var field : table.getFields()) {
            var fieldType = field.getType();
            var fieldName = field.getName();
            var auxfield = "empty";
            switch (fieldType.getName()) {
                case "int":
                    if(fieldType.isArray())
                        auxfield = "[I";
                    else
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

    private int calculateLocals(JmmNode methodDecl){
        int locals = 0;
        var params = methodDecl.getChildren("ParamDecl");
        // add params that were not used
        for(var param: params){
            if(!currentRegisters.containsKey(param.get("var"))){
                locals++;
            }
        }

        if(methodDecl.getObject("isStatic",Boolean.class)){
            locals += currentRegisters.size();
        }
        else{
            locals += currentRegisters.size()+1;
        }

        return locals;
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

        currNumInStack = 0;
        maxInStack = 0;

        exprGenerator = new JasminExprGeneratorVisitor(currentRegisters, table, currentMethod, currNumInStack, maxInStack);

        var code = new StringBuilder();

        // calculate modifier
        var modifier = methodDecl.getObject("isPublic", Boolean.class) ? "public " : "";

        if(methodDecl.getObject("isStatic",Boolean.class))
            code.append("\n.method ").append(modifier).append("static ").append(methodName).append("(");
        else
            code.append("\n.method ").append(modifier).append(methodName).append("(");

        String classes = "empty";
        for(var param : methodDecl.getChildren("ParamDecl")){
            var paramType = TypeUtils.getExprType(param, table, methodDecl.get("name"));
            switch(paramType.getName()){
                case "int":
                    if(paramType.isArray())
                        code.append("[I");
                    else
                        code.append("I");
                    break;
                case "boolean":
                    code.append("Z");
                    break;
                case "String":
                    if(paramType.isArray())
                        code.append("[Ljava/lang/String;");
                    else
                        code.append("Ljava/lang/String;");
                    break;
                default:
                    var importNode = methodDecl.getParent();

                    while(!importNode.getKind().equals("Program")) {
                        importNode = importNode.getParent();
                    }

                    var importNodes = importNode.getChildren("ImportDecl");
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

        // generate code for all statements
        var code_rest = new StringBuilder();

        for (var stmt : methodDecl.getChildren("Stmt")) {
            // Get code for statement, split into lines and insert the necessary indentation
            var instCode = StringLines.getLines(visit(stmt)).stream()
                    .collect(Collectors.joining(NL + TAB, TAB, NL));

            code_rest.append(instCode);
        }

        if(returnType.getName().equals("void"))
            code_rest.append("\treturn").append(NL);

        code_rest.append(".end method\n");

        code.append(".limit stack 99").append(NL);

        for(var registers: currentRegisters.entrySet()){
            System.out.println(registers.getKey() + " " + registers.getValue());
        }
        var locals = calculateLocals(methodDecl);
        code.append(".limit locals ").append(locals).append(NL);

        // reset information
        exprGenerator = null;
        nextRegister = -1;
        currentRegisters = null;
        currentMethod = null;

        code.append(code_rest);

        return code.toString();
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
            SpecsCheck.checkArgument(lhs.isInstance("Identifier") || lhs.isInstance("This") || lhs.isInstance("ParenOp"), () -> "Expected a node of type 'Identifier', but instead got '" + lhs.getKind() + "'");

            var destName = lhs.get("value");

            // get register
            var reg = currentRegisters.get(destName);

            if(!table.getImports().stream().anyMatch(importDecl -> importDecl.equals(destName))) {
                // If no mapping, variable has not been assigned yet, create mapping
                if (reg == null) {
                    reg = nextRegister;
                    currentRegisters.put(destName, reg);
                    nextRegister++;
                }
            }

            exprGenerator.visit(stmt, code);
        }

        return code.toString();
    }

    private boolean checkForIinc(JmmNode assignStmt, StringBuilder code){
        var lhs = assignStmt.getChild(0);
        var childExpr = assignStmt.getChild(1);

        while(childExpr.getKind().equals("ParenOp")){
            childExpr = childExpr.getChild(0);
        }

        if(childExpr.getKind().equals("BinaryExpr")){
            var operator = childExpr.get("op");
            if(operator.equals("+") || operator.equals("-")){
                if(childExpr.getDescendants("IntegerLiteral").size() == 1 && childExpr.getDescendants("Identifier").size() == 1){
                    var identifier = childExpr.getDescendants("Identifier").get(0);
                    var integerLiteral = childExpr.getDescendants("IntegerLiteral").get(0);
                    if(identifier.get("value").equals(lhs.get("value")) && (Integer.parseInt(integerLiteral.get("value")) > -127 && Integer.parseInt(integerLiteral.get("value")) < 128)){
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void checkForField(String destName, StringBuilder code){
        var isField = false;
        if(table.getFields().stream().anyMatch(param -> param.getName().equals(destName))){
            isField = true;
        }
        if(table.getParameters(currentMethod).stream().anyMatch(param -> param.getName().equals(destName)) ||
                table.getLocalVariables(currentMethod).stream().anyMatch(varDecl -> varDecl.getName().equals(destName))) {
            isField = false;
        }

        if(isField){
            code.append("aload_0").append(NL);
        }
    }

    private String visitAssignStmt(JmmNode assignStmt, Void unused) {
        var code = new StringBuilder();

        // store value in top of the stack in destination
        var lhs = assignStmt.getChild(0);
        SpecsCheck.checkArgument(lhs.isInstance("Identifier") || lhs.isInstance("This") || lhs.isInstance("ParenOp") || lhs.isInstance("ArrayAccessOp"), () -> "Expected a node of type 'Identifier', but instead got '" + lhs.getKind() + "'");

        String destName;
        if(lhs.isInstance("ArrayAccessOp")) {
            destName = lhs.getChild(0).get("value");

            checkForField(destName, code);

            exprGenerator.visit(assignStmt.getChild(0), code);

            // generate code that will put the value on the right on top of the stack
            exprGenerator.visit(assignStmt.getChild(1), code);

            code.append("iastore").append(NL);

            return code.toString();
        }
        else{
            destName = lhs.get("value");

            checkForField(destName, code);

            // get register
            var reg = currentRegisters.get(destName);

            // If no mapping, variable has not been assigned yet, create mapping
            if (reg == null) {
                reg = nextRegister;
                currentRegisters.put(destName, reg);
                nextRegister++;
            }

            boolean found = false;
            for (String key : currentRegisters.keySet()) {
                if (key.startsWith("temp_")) {
                    found = true;
                    break;
                }
            }
            if(found)
                updateRegisters(assignStmt);

            if(!assignStmt.getDescendants("BinaryExpr").isEmpty()){
                if(checkForIinc(assignStmt, code)){
                    code.append("iinc ").append(reg).append(" ").append(assignStmt.getChild(1).getChild(1).get("value")).append(NL);
                    return code.toString();
                }
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
                case "NewOpObject", "NewOpArray":
                    exprGenerator.loadAStore(reg, code);
                    break;
                case "IntegerLiteral", "BooleanLiteral":
                    if(isField)
                        code.append("putfield ").append(table.getClassName()).append("/").append(destName).append(" ").append(fieldType).append(NL);
                    else
                        exprGenerator.loadIStore(reg, code);
                    break;
            }

            return code.toString();
        }
    }

    private String visitReturnStmt(JmmNode returnStmt, Void unused) {

        var code = new StringBuilder();

        // generate code that will put the value of the return on the top of the stack
        exprGenerator.visit(returnStmt.getChild(0), code);
        var returnType = table.getReturnType(returnStmt.getAncestor("MethodDecl").get().get("name"));

        switch(returnType.getName()){
            case "int", "boolean":
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
}