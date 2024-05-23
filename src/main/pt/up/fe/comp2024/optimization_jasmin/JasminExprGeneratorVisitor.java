package pt.up.fe.comp2024.optimization_jasmin;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PostorderJmmVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.TypeUtils;
import pt.up.fe.specs.util.SpecsCheck;
import pt.up.fe.specs.util.exceptions.NotImplementedException;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class JasminExprGeneratorVisitor extends PostorderJmmVisitor<StringBuilder, Void> {

    private static final String NL = "\n";
    private final SymbolTable table;
    private String currentMethod;
    private int currNumInStack;
    private int maxInStack;
    private HashMap<String, Integer> numberOfArrayAccessCall = new HashMap<>();

    private final Map<String, Integer> currentRegisters;


    public JasminExprGeneratorVisitor(Map<String, Integer> currentRegisters, SymbolTable table, String methodName, int currNumInStack, int maxInStack){
        this.table = table;
        this.currentRegisters = currentRegisters;
        currentMethod = methodName;
        this.currNumInStack = currNumInStack;
        this.maxInStack = maxInStack;
    }

    @Override
    protected void buildVisitor() {
        // Using strings to avoid compilation problems in projects that
        // might no longer have the equivalent enums in Kind class.
        addVisit("IntegerLiteral", this::visitIntegerLiteral);
        addVisit("BooleanLiteral", this::visitBooleanLiteral);
        addVisit("Identifier", this::visitIdentifier);
        addVisit("BinaryExpr", this::visitBinaryExpr);
        addVisit("NewOpObject", this::visitNewOpObject);
        addVisit("MemberAccessOp", this::visitMemberAccessOp);
        addVisit("This", this::visitThisExpr);
        addVisit("ParenOp", this::visitParenOp);
        addVisit("NewOpArray", this::visitNewOpArray);
        addVisit("LengthOp", this::visitLengthOp);
        addVisit("ArrayAccessOp", this::visitArrayAccessOp);
        addVisit("ArrayCreationOp", this::visitArrayCreationOp);
    }

    public void setCurrNumInStack(int value){
        currNumInStack = value;
    }

    public void setMaxInStack(int value){
        maxInStack = value;
    }

    public void updateCurrNumInStack(int value){
        currNumInStack += value;
        updateMaxInStack();
    }

    public int getCurrNumInStack(){
        return currNumInStack;
    }

    public int getMaxInStack(){
        return maxInStack;
    }

    public void setRegisters(Map<String, Integer> registers, int nextRegisterHelp){
        currentRegisters.clear();
        currentRegisters.putAll(registers);
    }

    public void updateMaxInStack(){
        if(currNumInStack > maxInStack){
            maxInStack = currNumInStack;
        }
    }

    public void loadIStore(int reg, StringBuilder code) {
        if(reg <= 3)
            code.append("istore_").append(reg).append(NL);
        else
            code.append("istore ").append(reg).append(NL);
        updateCurrNumInStack(-1);
    }

    public void loadILoad(int reg, StringBuilder code) {
        if(reg <= 3)
            code.append("iload_").append(reg).append(NL);
        else
            code.append("iload ").append(reg).append(NL);
        updateCurrNumInStack(1);
    }

    public void loadAStore(int reg, StringBuilder code) {
        if(reg <= 3)
            code.append("astore_").append(reg).append(NL);
        else
            code.append("astore ").append(reg).append(NL);
        updateCurrNumInStack(-1);
    }

    public void loadALoad(int reg, StringBuilder code) {
        if(reg <= 3)
            code.append("aload_").append(reg).append(NL);
        else
            code.append("aload ").append(reg).append(NL);
        updateCurrNumInStack(1);
    }

    private Void checkConstantSize(int size, StringBuilder code) {
        if(size == -1){
            code.append("iconst_m1").append(NL);
        }
        if(size > -1 && size <= 5){
            code.append("iconst_").append(size).append(NL);
        }
        else if(size >= -127 && size <= 128){
            code.append("bipush ").append(size).append(NL);
        }
        else if(size >= -32768 && size <= 32767){
            code.append("sipush ").append(size).append(NL);
        }
        else{
            code.append("ldc ").append(size).append(NL);
        }
        updateCurrNumInStack(1);
        return null;
    }

    private Void visitIntegerLiteral(JmmNode integerLiteral, StringBuilder code) {
        if(integerLiteral.getParent().getKind().equals("ArrayCreationOp")
                || integerLiteral.getParent().getKind().equals("MemberAccessOp")){
            return null;
        }
        if(integerLiteral.getParent().getKind().equals("ArrayAccessOp")){
            if(!integerLiteral.getAncestor("AssignStmt").isEmpty()){
                var assignStmtLeftSideDescendants = integerLiteral.getAncestor("AssignStmt").get().getChild(0).getDescendants();
                if(!assignStmtLeftSideDescendants.contains(integerLiteral)){
                    return null;
                }
            }
        }
        if(integerLiteral.getAncestor("BinaryExpr").isPresent()){
            if(integerLiteral.getAncestor("BinaryExpr").get().hasAttribute("value")){
                return null;
            }
        }
        var value = Integer.parseInt(integerLiteral.get("value"));
        checkConstantSize(value, code);
        return null;
    }

    private Void visitBooleanLiteral(JmmNode booleanLiteral, StringBuilder code) {
        String value = switch (booleanLiteral.get("value")) {
            case "true" -> "1";
            case "false" -> "0";
            default -> "";
        };
        code.append("iconst_").append(value).append(NL);
        updateCurrNumInStack(1);
        return null;
    }

    private Void visitIdentifier(JmmNode idExpr, StringBuilder code) {
        if(idExpr.getParent().getKind().equals("ArrayAccessOp")){
            if(!idExpr.getAncestor("AssignStmt").isEmpty()){
                var assignStmtLeftSideDescendants = idExpr.getAncestor("AssignStmt").get().getChild(0).getDescendants();
                if(!assignStmtLeftSideDescendants.contains(idExpr)){
                    return null;
                }
            }
        }
        var name = idExpr.get("value");
        var type = TypeUtils.getExprType(idExpr, table, currentMethod);
        Type fieldType = null;
        String fieldTypeString = "";
        boolean isField = false;
        for(var field : table.getFields()) {
            if (field.getName().equals(name)) {
                isField = true;
                fieldType = field.getType();
                break;
            }
        }
        if(table.getImports().stream().anyMatch(importDecl -> importDecl.equals(name))){
            return null;
        }
        if (table.getParameters(currentMethod).stream().anyMatch(parameter -> parameter.getName().equals(name)) ||
                table.getLocalVariables(currentMethod).stream().anyMatch(variable -> variable.getName().equals(name))) {
            isField = false;
        }

        if(idExpr.getParent().getKind().equals("MemberAccessOp")) {
            return null;
        }

        if(isField){
            if(fieldType.getName().equals("int")){
                if(fieldType.isArray()){
                    fieldTypeString = "[I";
                }
                else{
                    fieldTypeString = "I";
                }
            }
            else if(fieldType.getName().equals("boolean"))
                fieldTypeString = "Z";
        }

        var reg = currentRegisters.get(name);
        SpecsCheck.checkNotNull(reg, () -> "No register mapped for variable '" + name + "'");

        if(isField) {
            var identifierBeingAssigned = currentRegisters.get(idExpr.getParent().getChild(0).get("value"));
            code.append("aload_0").append(NL);
            updateCurrNumInStack(1);
            code.append("getfield ").append(table.getClassName()).append("/").append(name).append(" ").append(fieldTypeString).append(NL);
            updateCurrNumInStack(-1); // consumes objectref
            updateCurrNumInStack(1); // puts result
            loadIStore(identifierBeingAssigned, code);
            loadILoad(identifierBeingAssigned, code);
        } else {
            if(code != null)
                if(TypeUtils.checkIfTypeIsPrimitive(type) && !type.isArray())
                    loadILoad(reg, code);
                else
                    loadALoad(reg, code);
        }

        return null;
    }

    private Void visitBinaryExpr(JmmNode binaryExpr, StringBuilder code) {

        if(binaryExpr.hasAttribute("value")){
            checkConstantSize(Integer.parseInt(binaryExpr.get("value")), code);
            return null;
        }

        // get the operation
        var op = switch (binaryExpr.get("op")) {
            case "/" -> "idiv";
            case "*" -> "imul";
            case "+" -> "iadd";
            case "-" -> "isub";
            case "<" -> "iflt";
            case "&&" -> "iand";
            default -> throw new NotImplementedException(binaryExpr.get("op"));
        };

        switch(op){
            case "iflt":
                updateCurrNumInStack(-1);
                break;
            default:
                updateCurrNumInStack(-2);
                updateCurrNumInStack(1);
                break;
        }
        code.append(op).append(NL);

        var parent = binaryExpr.getParent();

        while(parent.getKind().equals("ParenOp")){
            parent = parent.getParent();
        }

        switch(parent.getKind()){
            case "BinaryExpr":
                int reg = 0;
                if(currentRegisters.containsValue(0)) reg = currentRegisters.size();
                else reg = currentRegisters.size() + 1;
                currentRegisters.put("temp_" + reg, reg);

                loadIStore(reg, code);
                loadILoad(reg, code);
                break;
            case "AssignStmt":
                var name = binaryExpr.getParent().getChild(0).get("value"); // TODO - Could be array access
                var regAssign = currentRegisters.get(name);

                // If no mapping, variable has not been assigned yet, create mapping
                if (regAssign == null) {
                    regAssign = currentRegisters.size();
                    currentRegisters.put(name, regAssign);
                }
                if(currentRegisters.containsKey("temp_" + regAssign)){
                    if(currentRegisters.containsValue(0)) regAssign = currentRegisters.size();
                    else regAssign = currentRegisters.size() + 1;
                    currentRegisters.remove(name);
                    currentRegisters.put(name, regAssign);
                }
                loadIStore(regAssign, code);
                break;
        }

        return null;
    }


    private Void visitNewOpObject(JmmNode newOp, StringBuilder code) {
        var name = "";
        if(!newOp.getParent().getKind().equals("ParenOp")){
            name = newOp.getParent().getChild(0).get("value");
        }
        var reg = currentRegisters.get(name);

        // If no mapping, variable has not been assigned yet, create mapping
        if (reg == null) {
            reg = currentRegisters.size();
            currentRegisters.put(name, reg);
        }

        var program = newOp.getParent();

        while(!program.getKind().equals("Program")) {
            program = program.getParent();
        }

        boolean isImport = false;
        var importName = "";

        for(var child: program.getChildren(Kind.IMPORT_DECL)){
            var iName = child.get("importName");
            var importName2 = iName.replace("[", "").replace("]", "").replace(" ", "");
            var importNameList = importName2.split(",");
            for(var importNameElement: importNameList){
                if(importNameElement.equals(newOp.get("value"))){
                    isImport = true;
                    importName = iName;
                }
            }
        }

        if(isImport){
            var classes = importName
                    .replace("[", "")
                    .replace("]", "")
                    .replace(" ", "")
                    .replace(",","/");
            code.append("new ").append(classes).append(NL);
            updateCurrNumInStack(1);
            code.append("dup").append(NL);
            updateCurrNumInStack(1);
            code.append("invokespecial ").append(classes).append("/<init>()V").append(NL);
            updateCurrNumInStack(-1);
            updateCurrNumInStack(1);
            return null;
        }
        else{
            code.append("new ").append(newOp.get("value")).append(NL);
            updateCurrNumInStack(1);
            code.append("dup" + NL);
            updateCurrNumInStack(1);
            loadAStore(reg, code);
            loadALoad(reg, code);
            code.append("invokespecial ").append(newOp.get("value")).append("/<init>()V").append(NL);
            updateCurrNumInStack(-1);
            updateCurrNumInStack(1);
            return null;
        }
    }

    private Void visitMemberAccessOp(JmmNode memberAccessOp, StringBuilder code) {
        var funcName = memberAccessOp.get("func");
        var memberAccessType = TypeUtils.getExprType(memberAccessOp, table, currentMethod);
        boolean isPrimitive = TypeUtils.checkIfTypeIsPrimitive(memberAccessType);
        // get register for object
        var firstChild = memberAccessOp.getChild(0);
        if(memberAccessOp.getChild(0).getKind().equals("ParenOp")){
            for(var child: memberAccessOp.getChild(0).getDescendants()){
                if(!child.getKind().equals("ParenOp")){
                    firstChild = child;
                }
            }
        }
        var name = firstChild.get("value");
        var reg = currentRegisters.get(name);

        // If no mapping, variable has not been assigned yet, create mapping, unless for this and imports
        if (reg == null && !name.equals("this") && !table.getImports().contains(name)) {
            reg = currentRegisters.size();
            currentRegisters.put(name, reg);
        }
        // aload object if it is a class method and not directly from import
        if(!firstChild.getKind().equals("This") && !table.getImports().contains(name)){
            loadALoad(reg, code);
        }

        // load parameters
        var numOfParams = memberAccessOp.getNumChildren() - 1;
        for(var child : memberAccessOp.getChildren().subList(1, memberAccessOp.getNumChildren())) {
            if (child.getKind().equals("ParenOp")) {
                for (var grandChild : child.getDescendants()) {
                    if (!grandChild.getKind().equals("ParenOp")) {
                        child = grandChild;
                    }
                }
            }

            var idType = TypeUtils.getExprType(child, table, currentMethod);
            String childName;

            if(child.getKind().equals("LengthOp")) {
                childName = child.getChild(0).get("value") + ".length";
            } else if (child.getKind().equals("ArrayAccessOp")) {
                childName = child.getChild(0).get("value");
            } else if (child.getKind().equals("BinaryExpr")) {
                    if(currentRegisters.containsValue(0)) reg = currentRegisters.size();
                    else reg = currentRegisters.size() + 1;

                    currentRegisters.put("temp_" + reg, reg);
                    loadIStore(reg, code);
                    childName = "temp_" + reg;
            } else if (!child.getKind().equals("MemberAccessOp")) {
                childName = child.get("value");
            } else {
                childName = child.get("func");
            }

            boolean isField = false;
            Type fieldType = null;
            String fieldTypeString = "";
            for(var field : table.getFields()) {
                if (field.getName().equals(childName)) {
                    isField = true;
                    fieldType = field.getType();
                    break;
                }
            }
            if (table.getParameters(currentMethod).stream().anyMatch(parameter -> parameter.getName().equals(childName)) ||
                    table.getLocalVariables(currentMethod).stream().anyMatch(variable -> variable.getName().equals(childName))) {
                isField = false;
            }

            if (isField) {
                if(fieldType.getName().equals("int")) {
                    if (fieldType.isArray()){
                        fieldTypeString = "[I";
                    }
                    else{
                        fieldTypeString = "I";
                    }
                }
                else if(fieldType.getName().equals("boolean"))
                    fieldTypeString = "Z";
                code.append("aload_0").append(NL);
                updateCurrNumInStack(1);
                code.append("getfield ").append(table.getClassName()).append("/").append(childName).append(" ").append(fieldTypeString).append(NL);
                updateCurrNumInStack(-1);
                updateCurrNumInStack(1);
                reg = currentRegisters.get(childName);

                // If no mapping, variable has not been assigned yet, create mapping
                if (reg == null) {
                    reg = currentRegisters.size();
                    currentRegisters.put(childName, reg);
                }
                loadIStore(reg, code);
                loadILoad(reg, code);
            } else {
                if (!child.getKind().equals("IntegerLiteral") && !child.getKind().equals("BooleanLiteral")) {

                    reg = currentRegisters.get(childName);

                    // If no mapping, variable has not been assigned yet, create mapping
                    if (reg == null) {
                        reg = currentRegisters.size();
                        currentRegisters.put(childName, reg);
                    }

                    if(child.getKind().equals("ArrayAccessOp")){
                        // do nothing
                    } else if (idType.getName().equals(TypeUtils.getIntTypeName()) && !idType.isArray()) {
                        loadILoad(currentRegisters.get(childName), code);
                    } else if (idType.getName().equals(TypeUtils.getBooleanTypeName())) {
                        loadILoad(currentRegisters.get(childName), code);
                    } else {
                        loadALoad(currentRegisters.get(childName), code);
                    }
                }
                else if (child.getKind().equals("IntegerLiteral")){
                    checkConstantSize(Integer.parseInt(child.get("value")), code);
                }
            }
        }

        // TODO - Check if it removes from stack
        if(firstChild.getKind().equals("This")){
            code.append("invokevirtual ");
            updateCurrNumInStack(-1);
            updateCurrNumInStack(-numOfParams);
            updateCurrNumInStack(1);
        }
        else if(table.getImports().contains(name) || table.getMethods().contains(memberAccessType.getName())){
            code.append("invokestatic ");
            updateCurrNumInStack(-numOfParams);
            updateCurrNumInStack(1);
        }
        else{
            code.append("invokevirtual ");
            updateCurrNumInStack(-1);
            updateCurrNumInStack(-numOfParams);
            updateCurrNumInStack(1);
        }

        if(isPrimitive){
            code.append(table.getClassName()).append("/");
        }
        else{
            var importNode = memberAccessOp.getParent();

            while(!importNode.getKind().equals("Program")) {
                importNode = importNode.getParent();
            }

            var importNodes = importNode.getChildren("ImportDecl");
            for(var anImport: importNodes) {
                var classes = anImport.get("importName")
                        .replace("[", "")
                        .replace("]", "")
                        .replace(" ", "")
                        .replace(",","/");

                var auxLast = classes.split("/");
                if(Objects.equals(auxLast[auxLast.length - 1], memberAccessType.getName())) {
                    code.append(classes).append("/");
                    break;
                }
            }


        }

        code.append(funcName).append("(");

        for(var child : memberAccessOp.getChildren().subList(1, memberAccessOp.getNumChildren())){
            var paramType = TypeUtils.getExprType(child, table, currentMethod);
            if(paramType.getName().equals(TypeUtils.getIntTypeName())){
                if(paramType.isArray()){
                    code.append("[I");
                }
                else{
                    code.append("I");
                }
            }
            else if(paramType.getName().equals(TypeUtils.getBooleanTypeName())){
                code.append("Z");
            }
            else if(table.getImports().contains(paramType.getName())){
                var program = memberAccessOp.getParent();
                while(!program.getKind().equals("Program")) {
                    program = program.getParent();
                }
                var classes = "";
                for(var imports: program.getDescendants(Kind.IMPORT_DECL)){
                    if(imports.get("importName").contains(memberAccessType.getName())){
                        classes = imports.get("importName")
                                .replace("[", "")
                                .replace("]", "")
                                .replace(" ", "")
                                .replace(",","/");
                    }
                }
                code.append("L").append(classes).append(";");
            }
        }
        code.append(")");

        switch(memberAccessType.getName()){
            case "Integer", "int":
                if(memberAccessType.isArray())
                    code.append("[I");
                else
                    code.append("I");
                break;
            case "Boolean", "boolean":
                code.append("Z");
                break;
            default:
                code.append("V");
                break;
        }

        boolean isReturn = true;
        if(memberAccessOp.getParent().getKind().equals("ExprStmt")){
            isReturn = false;
        }
        if(isReturn && !memberAccessOp.getAncestor("BinaryExpr").isPresent()){
            code.append(NL);
            // Return store
            if(TypeUtils.checkIfTypeIsPrimitive(TypeUtils.getExprType(memberAccessOp, table, currentMethod)) && !memberAccessOp.getAncestor("BinaryExpr").isPresent()){
                if(memberAccessOp.getAncestor("AssignStmt").isPresent() && !memberAccessOp.getAncestor("MemberAccessOp").isPresent()){
                    name = memberAccessOp.getAncestor("AssignStmt").get().getChild(0).get("value");
                    reg = currentRegisters.get(name);

                    // If no mapping, variable has not been assigned yet, create mapping
                    if (reg == null) {
                        reg = currentRegisters.size();
                        currentRegisters.put(name, reg);
                    }
                    loadIStore(reg, code);
                }
                else{
                    var func = memberAccessOp.get("func");
                    reg = currentRegisters.get(func);

                    // If no mapping, variable has not been assigned yet, create mapping
                    if (reg == null) {
                        reg = currentRegisters.size();
                        currentRegisters.put(func, reg);
                    }
                    loadIStore(reg, code);
                }
            }
            else{
                if(memberAccessOp.getAncestor("AssignStmt").isPresent() && !memberAccessOp.getAncestor("MemberAccessOp").isPresent()){
                    name = memberAccessOp.getAncestor("AssignStmt").get().getChild(0).get("value");
                    reg = currentRegisters.get(name);

                    // If no mapping, variable has not been assigned yet, create mapping
                    if (reg == null) {
                        reg = currentRegisters.size();
                        currentRegisters.put(name, reg);
                    }
                    loadAStore(reg, code);
                }
                else{
                    var func = memberAccessOp.get("func");
                    reg = currentRegisters.get(func);

                    // If no mapping, variable has not been assigned yet, create mapping
                    if (reg == null) {
                        reg = currentRegisters.size();
                        currentRegisters.put(func, reg);
                    }
                    loadAStore(reg, code);
                }
            }
        }
        else if(isReturn && memberAccessOp.getAncestor("BinaryExpr").isPresent()){
            code.append(NL);
            if(currentRegisters.containsValue(0)) reg = currentRegisters.size();
            else reg = currentRegisters.size() + 1;
            currentRegisters.put("temp_" + reg, reg);
            loadIStore(reg, code);
            loadILoad(reg, code);
        }
        else{
            code.append(NL);
        }

        return null;
    }

    private Void visitParenOp(JmmNode parenStmt, StringBuilder code) {
        return null;
    }

    private Void visitThisExpr(JmmNode thisExpr, StringBuilder code) {
        if(thisExpr.getParent().getKind().equals("AssignStmt")){
            code.append("new ").append(table.getClassName()).append(NL);
            code.append("dup").append(NL);
            updateCurrNumInStack(2);
        }
        else{
            code.append("aload_0").append(NL);
            updateCurrNumInStack(1);
        }
        return null;
    }

    private Void visitNewOpArray(JmmNode newOpArray, StringBuilder code) {
        var name = newOpArray.getParent().getChild(0).get("value");
        var reg = currentRegisters.get(name);

        // If no mapping, variable has not been assigned yet, create mapping
        if (reg == null) {
            reg = currentRegisters.size();
            currentRegisters.put(name, reg);
        }

        code.append("newarray int").append(NL);
        updateCurrNumInStack(-1);
        updateCurrNumInStack(1);

        return null;
    }

    private Void visitLengthOp(JmmNode lengthOp, StringBuilder code) {
        var name = lengthOp.getChild(0).get("value") + ".length";
        var reg = currentRegisters.get(name);

        // If no mapping, variable has not been assigned yet, create mapping
        if (reg == null) {
            reg = currentRegisters.size();
            currentRegisters.put(name, reg);
        }

        code.append("arraylength").append(NL);
        updateCurrNumInStack(-1);
        updateCurrNumInStack(1);
        loadIStore(reg, code);
        if(lengthOp.getParent().getKind().equals("AssignStmt") || lengthOp.getParent().getKind().equals("BinaryExpr")){
            loadILoad(reg, code);
        }

        return null;
    }

    private Void visitArrayAccessOp(JmmNode arrayAccessOp, StringBuilder code) {
        if(arrayAccessOp.getParent().getKind().equals("ReturnStmt")){
            var reg = currentRegisters.get(arrayAccessOp.getChild(0).get("value"));

            // If no mapping, variable has not been assigned yet, create mapping
            if (reg == null) {
                if(currentRegisters.containsValue(0)) reg = currentRegisters.size();
                else reg = currentRegisters.size() + 1;
                currentRegisters.put(arrayAccessOp.getChild(0).get("value"), reg);
            }

            code.append("iaload").append(NL);
            updateCurrNumInStack(-2);
            updateCurrNumInStack(1);
        }
        else if(arrayAccessOp.getParent().getKind().equals("MemberAccessOp")){
            switch(arrayAccessOp.getChild(1).getKind()){
                case "BinaryExpr":
                    // store the binary result
                    int regInt = 0;
                    if(currentRegisters.containsValue(0)) regInt = currentRegisters.size();
                    else regInt = currentRegisters.size() + 1;
                    currentRegisters.put("temp_" + regInt, regInt);
                    loadIStore(regInt, code);

                    var reg = currentRegisters.get(arrayAccessOp.getChild(0).get("value"));

                    // If no mapping, variable has not been assigned yet, create mapping
                    if (reg == null) {
                        if(currentRegisters.containsValue(0)) reg = currentRegisters.size();
                        else reg = currentRegisters.size() + 1;
                        currentRegisters.put(arrayAccessOp.getChild(0).get("value"), reg);
                    }

                    loadALoad(reg, code);
                    // load the result
                    loadILoad(regInt, code);
                    code.append("iaload").append(NL);
                    // store temporary for the binary
                    regInt = 0;
                    if(currentRegisters.containsValue(0)) regInt = currentRegisters.size();
                    else regInt = currentRegisters.size() + 1;
                    currentRegisters.put("temp_" + regInt, regInt);
                    loadIStore(regInt, code);
                    loadILoad(regInt, code);
                    updateCurrNumInStack(-2);
                    updateCurrNumInStack(1);
                    break;
                case "ArrayAccessOp":
                    // load the inner array
                    var regArray = currentRegisters.get(arrayAccessOp.getChild(1).getChild(0).get("value"));

                    // If no mapping, variable has not been assigned yet, create mapping
                    if (regArray == null) {
                        regArray = currentRegisters.size();
                        currentRegisters.put(arrayAccessOp.getChild(1).getChild(0).get("value"), regArray);
                    }

                    loadALoad(regArray, code);
                    // load the inner array index
                    checkConstantSize(Integer.parseInt(arrayAccessOp.getChild(1).getChild(1).get("value")), code);

                    code.append("iaload").append(NL);
                    updateCurrNumInStack(-2);
                    updateCurrNumInStack(1);

                    // store and load temporary for the array access
                    int tempreg = 0;
                    if(currentRegisters.containsValue(0)) tempreg = currentRegisters.size();
                    else tempreg = currentRegisters.size() + 1;
                    currentRegisters.put("temp_" + tempreg, tempreg);
                    loadIStore(tempreg, code);

                    // load the outside array
                    var regArray2 = currentRegisters.get(arrayAccessOp.getChild(0).get("value"));

                    // If no mapping, variable has not been assigned yet, create mapping
                    if (regArray2 == null) {
                        regArray2 = currentRegisters.size();
                        currentRegisters.put(arrayAccessOp.getChild(0).get("value"), regArray2);
                    }

                    loadALoad(regArray2, code);
                    // load the temporary
                    loadILoad(tempreg, code);
                    code.append("iaload").append(NL);
                    updateCurrNumInStack(-2);
                    updateCurrNumInStack(1);

                    // store and load temporary for the array access
                    int tempRegInt = 0;
                    if(currentRegisters.containsValue(0)) tempRegInt = currentRegisters.size();
                    else tempRegInt = currentRegisters.size() + 1;
                    currentRegisters.put("temp_" + tempRegInt, tempRegInt);
                    loadIStore(tempRegInt, code);
                    loadILoad(tempRegInt, code);
                    break;
                case "MemberAccessOp":
                    // load array
                    var regMemberArray = currentRegisters.get(arrayAccessOp.getChild(0).get("value"));

                    // If no mapping, variable has not been assigned yet, create mapping
                    if (regMemberArray == null) {
                        regMemberArray = currentRegisters.size();
                        currentRegisters.put(arrayAccessOp.getChild(0).get("value"), regMemberArray);
                    }

                    loadALoad(regMemberArray, code);

                    // search for the member access return register
                    var regResult = currentRegisters.get(arrayAccessOp.getChild(1).get("func"));

                    // if no mapping, variable has not been assigned yet, create mapping
                    if (regResult == null) {
                        if(currentRegisters.containsValue(0)) regResult = currentRegisters.size();
                        else regResult = currentRegisters.size() + 1;
                        currentRegisters.put(arrayAccessOp.getChild(1).get("func"), regResult);
                    }

                    // load the latest temporary register
                    loadILoad(regResult, code);
                    code.append("iaload").append(NL);
                    updateCurrNumInStack(-2);

                    // store and load temporary for the member access
                    tempreg = 0;
                    if(currentRegisters.containsValue(0)) tempreg = currentRegisters.size();
                    else tempreg = currentRegisters.size() + 1;
                    currentRegisters.put("temp_" + tempreg, tempreg);
                    loadIStore(tempreg, code);
                    loadILoad(tempreg, code);
                    updateCurrNumInStack(1);
                    break;
                case "Identifier":
                    // load array and identifier
                    regArray2 = currentRegisters.get(arrayAccessOp.getChild(0).get("value"));

                    // If no mapping, variable has not been assigned yet, create mapping
                    if (regArray2 == null) {
                        regArray2 = currentRegisters.size();
                        currentRegisters.put(arrayAccessOp.getChild(0).get("value"), regArray2);
                    }

                    loadALoad(regArray2, code);

                    var regIdentifier = currentRegisters.get(arrayAccessOp.getChild(1).get("value"));

                    // If no mapping, variable has not been assigned yet, create mapping
                    if (regIdentifier == null) {
                        regIdentifier = currentRegisters.size();
                        currentRegisters.put(arrayAccessOp.getChild(1).get("value"), regIdentifier);
                    }

                    loadILoad(regIdentifier, code);
                    code.append("iaload").append(NL);
                    updateCurrNumInStack(-2);

                    // store and load temporary for the member access
                    tempRegInt = 0;
                    if(currentRegisters.containsValue(0)) tempRegInt = currentRegisters.size();
                    else tempRegInt = currentRegisters.size() + 1;
                    currentRegisters.put("temp_" + tempRegInt, tempRegInt);
                    loadIStore(tempRegInt, code);
                    loadILoad(tempRegInt, code);
                    updateCurrNumInStack(1);
                    break;
                default: // IntegerLiteral
                    code.append("iaload").append(NL);
                    updateCurrNumInStack(-2);
                    updateCurrNumInStack(1);

                    // store and load temporary for the array access
                    tempRegInt = 0;
                    if(currentRegisters.containsValue(0)) tempRegInt = currentRegisters.size();
                    else tempRegInt = currentRegisters.size() + 1;
                    currentRegisters.put("temp_" + tempRegInt, tempRegInt);
                    loadIStore(tempRegInt, code);
                    loadILoad(tempRegInt, code);
                    break;
            }
        }
        return null;
    }

    private Void visitArrayCreationOp(JmmNode arrayCreationOp, StringBuilder code) {
        var variables = arrayCreationOp.getChildren();
        int size = variables.size();

        checkConstantSize(size, code);

        code.append("newarray int").append(NL);
        updateCurrNumInStack(-1);
        updateCurrNumInStack(1);

        for(int i = 0; i < variables.size(); i++){
            code.append("dup").append(NL);
            updateCurrNumInStack(1);
            checkConstantSize(i, code);
            checkConstantSize(Integer.parseInt(variables.get(i).get("value")), code);
            code.append("iastore").append(NL);
            updateCurrNumInStack(-3);
        }

        var reg = currentRegisters.get(arrayCreationOp.getParent().getChild(0).get("value"));

        // If no mapping, variable has not been assigned yet, create mapping
        if (reg == null) {
            reg = currentRegisters.size();
            currentRegisters.put(arrayCreationOp.getParent().getChild(0).get("value"), reg);
        }

        loadAStore(reg, code);

        return null;
    }
}
