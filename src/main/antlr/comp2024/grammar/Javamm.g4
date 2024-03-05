grammar Javamm;

@header {
    package pt.up.fe.comp2024;
}

EQUALS : '=';
SEMI : ';' ;
LCURLY : '{' ;
RCURLY : '}' ;
LPAREN : '(' ;
RPAREN : ')' ;
MUL : '*' ;
ADD : '+' ;
SUB : '-';
DIV : '/';
DOT : '.';
ELLIPSIS : '...';
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
MAIN : 'main' ;
STRING : 'String' ;
IMPORT : 'import';
EXTENDS : 'extends';
LENGTH : 'length';

INTEGER : '0' | [1-9][0-9]* ;
ID : [a-zA-Z_$] [a-zA-Z0-9_$]*;

ML_COMMENT : '/*' .*? '*/' -> skip ;
EOL_COMMENT : '//' ~[\r\n]* -> skip ;

WS : [ \t\n\r\f]+ -> skip ;

program
    : (importDeclaration)* classDeclaration EOF
    ;

importDeclaration
    : op=IMPORT name=ID ( DOT extension=ID )* SEMI #ImportDecl
    ;

classDeclaration
    : CLASS name=ID ( EXTENDS extension=ID )? LCURLY ( varDeclaration )* ( methodDeclaration )* RCURLY #ClassDecl
    ;

varDeclaration
    : type name=ID SEMI #VarDecl
    ;

methodDeclaration locals [boolean isPublic=false]
    : (PUBLIC {$isPublic=true;})? type name=ID LPAREN ( param ( COMMA param )* )? RPAREN LCURLY (varDeclaration)* ( stmt )* RETURN expr SEMI RCURLY #MethodReturnDecl
    | (PUBLIC {$isPublic=true;})? STATIC VOID name=MAIN LPAREN STRING LSQUARE RSQUARE ID RPAREN LCURLY ( varDeclaration )* ( stmt )* RCURLY #MainMethodDecl
    ;

param
    : type var=ID
    ;

type locals [boolean isArray=false]
    : INT (LSQUARE RSQUARE {$isArray=true;})? #IntArrayType
    | INT ELLIPSIS #IntEllipsisType
    | BOOLEAN #BooleanType
    | INT #IntType
    | STRING #StringType
    | ID #IdentifierType
    | (anIntArray {$isArray=true;})? #AnIntArrayType
    ;

stmt
    : LCURLY ( stmt )* RCURLY #Block
    | IF LPAREN expr RPAREN stmt ELSE stmt #IfElse
    | WHILE LPAREN expr RPAREN stmt #While
    | expr SEMI #ExprStmt
    | var=ID EQUALS expr SEMI #Assign
    | var=ID LSQUARE expr RSQUARE EQUALS expr SEMI #ArrayAssign
    ;
expr
    : expr (op+=DOT func=LENGTH | op+=DOT value+=ID LPAREN ( expr ( COMMA expr )* )? RPAREN | LSQUARE expr RSQUARE) #MemberOrArrayAccessOp
    | op+=LPAREN expr RPAREN #ParenOp
    | op+=NEG expr #UnaryOp
    | (op+=NEW value+=INT LSQUARE expr RSQUARE | op+=NEW value+=ID LPAREN RPAREN) #NewOp
    | expr op+=(MUL | DIV) expr #BinaryOp
    | expr op+=(ADD | SUB) expr #BinaryOp
    | expr op+=LT expr #BinaryOp
    | expr op+=AND expr #BinaryOp
    | LSQUARE ( expr ( COMMA expr )* )? RSQUARE #ArrayCreation
    | value+=INT #Int
    | value+=TRUE #True
    | value+=FALSE #False
    | value+=ID #Identifier
    | value+=THIS #This
    | value+=NULL #Null
    | value+=INTEGER #Integer
    ;

anIntArray
    : INT LSQUARE ( INTEGER ( COMMA INTEGER )* )? RSQUARE
    ;
