grammar Javamm;

@header {
    package pt.up.fe.comp2024;
}

EQUALS : '=';
SEMI : ';';
LCURLY : '{';
RCURLY : '}';
LPAREN : '(';
RPAREN : ')';
MUL : '*';
ADD : '+';
SUB : '-';
DIV : '/';
DOT : '.';
INT_ELLIPSIS : 'int...';
LSQUARE : '[';
RSQUARE : ']';
COMMA : ',';
AND : '&&';
LT : '<';
NEG : '!';

CLASS : 'class' ;
INT : 'int' ;
PUBLIC : 'public' ;
RETURN : 'return' ;
TRUE : 'true' ;
FALSE : 'false' ;
THIS : 'this' ;
NULL : 'null' ;
IF : 'if' ;
ELSE : 'else' ;
WHILE : 'while' ;
NEW : 'new' ;
BOOLEAN : 'boolean' ;
STATIC : 'static' ;
VOID : 'void' ;
STRING : 'String' ;
IMPORT : 'import';
EXTENDS : 'extends';

INTEGER : '0' | [1-9][0-9]* ;
ID : [a-zA-Z_$][a-zA-Z0-9_$]*;

ML_COMMENT : '/*' .*? '*/' -> skip ;
EOL_COMMENT : '//' ~[\r\n]* -> skip ;

WS : [ \t\n\r\f]+ -> skip ;

program
    : (importDeclaration)* classDeclaration EOF
    ;

importDeclaration
    : IMPORT importName+=ID ( DOT importName+=ID )* SEMI #ImportDecl
    ;

classDeclaration
    : CLASS className=ID ( EXTENDS extension=ID )? LCURLY ( varDeclaration )* ( methodDeclaration )* RCURLY #ClassDecl
    ;

varDeclaration
    : type varName=ID SEMI #VarDecl
    ;

methodDeclaration locals [boolean isPublic=false, boolean isStatic=false]
    : (PUBLIC {$isPublic=true;})? (STATIC {$isStatic=true;})? type name=ID LPAREN ( param ( COMMA param )* )? RPAREN LCURLY (varDeclaration)* ( stmt )* RCURLY #MethodDecl
    ;

param
    : type var=ID #ParamDecl
    ;

type locals [boolean isArray=false, boolean isVarargs=false]
    : typeName=INT LSQUARE RSQUARE {$isArray=true;} #IntArrayType
    | typeName=INT_ELLIPSIS {$isVarargs=true; $isArray=true;} #IntEllipsisType
    | typeName=BOOLEAN #BooleanType
    | typeName=INT #IntType
    | typeName=STRING #StringType
    | typeName=STRING LSQUARE RSQUARE {$isArray=true;} #StringArrayType
    | typeName=ID #IdentifierType
    | typeName=VOID #VoidType
    ;

stmt
    : LCURLY ( stmt )* RCURLY #BlockStmt
    | IF LPAREN expr RPAREN stmt ELSE stmt #IfElseStmt
    | WHILE LPAREN expr RPAREN stmt #WhileStmt
    | expr SEMI #ExprStmt
    | expr EQUALS expr SEMI #AssignStmt
    | expr LSQUARE expr RSQUARE EQUALS expr SEMI #ArrayAssign
    | RETURN expr SEMI #ReturnStmt
    ;
expr
    : expr op=DOT value=ID #LengthOp
    | expr op=DOT func=ID LPAREN ( expr ( COMMA expr )* )? RPAREN #MemberAccessOp
    | expr (LSQUARE expr RSQUARE) #ArrayAccessOp
    | op=LPAREN expr RPAREN #ParenOp
    | op=NEG expr #UnaryOp
    | op=NEW value=INT LSQUARE expr RSQUARE #NewOpArray
    | op=NEW value=ID LPAREN RPAREN #NewOpObject
    | expr op=(MUL | DIV) expr #BinaryExpr
    | expr op=(ADD | SUB) expr #BinaryExpr
    | expr op=LT expr #BinaryExpr
    | expr op=AND expr #BinaryExpr
    | LSQUARE ( expr ( COMMA expr )* )? RSQUARE #ArrayCreationOp
    | value=INT #Int
    | value=TRUE #BooleanLiteral
    | value=FALSE #BooleanLiteral
    | value=ID #Identifier
    | value=THIS #This
    | value=NULL #Null
    | value=INTEGER #IntegerLiteral
    ;
