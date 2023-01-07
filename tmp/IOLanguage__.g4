grammar IOLanguage__;

iolanguage: EOL* expressionList? EOF;

expressionList:
    expression (terminator+ expression terminator*)* EOL*;

expression: 
    expression op=('@' | '@@' | '\'' | '.' | '?' | ':') expression
    | expression op='**' expression
    | expression op=('++' | '--') expression
    | expression op=('*' | '/' | '%') expression
    | expression op=('+' | '-') expression
    | expression op=('<<' | '>>') expression
    | expression op=('<' | '>' | '<=' | '>=') expression
    | expression op=('==' | '!=') expression
    | expression op='&' expression
    | expression op='^' expression
    | expression op='|' expression
    | expression op=('and' | '&&') expression
    | expression op=('or' | '||') expression
    | expression op='..' expression
    | literal? message* name=IDENTIFIER assign=('+=' | '-=' | '*=' | '/=' | '%=' | '&=' | '^=' | '|=' | '<<=' | '>>=' | '<-' | '<->' | '->') expression
    | literal? message* name=IDENTIFIER assign=('=' | ':=' | '::=') expression
    | returnMessage
    | breakMessage
    | continueMessage
    | ifMessage
    | whileMessage 
    // | forMessage
    | listMessage
    | methodMessage
    | message+
    | literal message*
    | OPEN EOL* expressionList? CLOSE message*;

message: 
    GETSLOT OPEN EOL* name=STRING EOL* CLOSE
    | symbol arguments?;
arguments: OPEN (argument (COMMA argument)*)? CLOSE;
argument: EOL* expression EOL*;

returnMessage: RETURN expression?;
breakMessage: BREAK;
continueMessage: CONTINUE;
listMessage: LIST arguments?;
methodMessage: METHOD OPEN EOL* parameterList? body=expressionList? EOL* CLOSE;
parameterList: (IDENTIFIER EOL* COMMA EOL*)+;
ifMessage: IF OPEN EOL* 
        condition=expressionList EOL* COMMA EOL* 
        thenPart=expressionList EOL* (COMMA EOL* 
        elsePart=expressionList EOL*)? 
    CLOSE;
whileMessage: WHILE OPEN EOL* 
        condition=expressionList EOL* COMMA EOL* 
        body=expressionList EOL* 
    CLOSE;
// forMessage: FOR OPEN EOL* 
//         counter=IDENTIFIER EOL* COMMA EOL* 
//         startPart=expressionList EOL* COMMA EOL* 
//         endPart=expressionList EOL* COMMA EOL* 
//         (stepPart=expressionList EOL* COMMA EOL*)?
//         body=expressionList EOL* 
//     CLOSE;

symbol: id=IDENTIFIER | op=OPERATOR;
literal: 
    num=NUMBER 
    | str=STRING
    | pseudoVariable;
pseudoVariable: NIL | TRUE | FALSE | SELF | SUPER;

terminator: ';' | EOL;

BREAK: 'break';
CONTINUE: 'continue';
FALSE: 'false';
//FOR: 'for';
GETSLOT: 'getSlot';
IF: 'if';
LIST: 'list';
METHOD: 'method';
NIL: 'nil';
RETURN: 'return';
SELF: 'self';
// SETSLOT: 'setSlot';
SUPER: 'super';
TRUE: 'true';
WHILE: 'while';

LINE_JOINS: '\\' [ \t]* RN -> channel(HIDDEN);
EOL: RN;

WS: [ \t\u000C]+ -> channel(HIDDEN);
COMMENT: '/*' .*? '*/' -> channel(HIDDEN);
LINE_COMMENT: ('//' | '#') ~[\r\n]* -> channel(HIDDEN);

NUMBER:
    DECIMAL_INTEGER 
    | HEX_NUMBER 
    | OCT_NUMBER 
    | BIN_NUMBER 
    | FLOAT_NUMBER;

fragment LETTER: [A-Z] | [a-z];
fragment NON_ZERO_DIGIT: [1-9];
fragment DIGIT: [0-9];
fragment HEX_DIGIT: [0-9] | [a-f] | [A-F];
fragment OCT_DIGIT: [0-7];
fragment BIN_DIGIT: [01];
fragment TAB: '\t';
fragment STRING_CHAR: ~('"' | '\r' | '\n');
fragment RN: '\r'? '\n';

fragment EXPONENT_OR_POINT_FLOAT
    : ([0-9]+ | POINT_FLOAT) [eE] [+-]? [0-9]+
    | POINT_FLOAT
    ;

fragment POINT_FLOAT
    : [0-9]* '.' [0-9]+
    | [0-9]+ '.'
    ;

IDENTIFIER: (LETTER | '_') (LETTER | '_' | DIGIT)*;

OPERATOR:  (':' | '.' | '\'' | '~' | '!' | '@' | '$' | 
    '%' | '^' | '&' | '*' | '-' | '+' | '/' | '=' | '{' | '}' | 
    '[' | ']' | '|' | '\\' | '<' | '>' | '?')+;

COMMA: ',';
OPEN: '(' | '[' | '{';
CLOSE:')' | ']' | '}';
STRING: MONOQUOTE | TRIQUOTE;
MONOQUOTE: '"' STRING_CHAR* '"';
TRIQUOTE: '"""' STRING_CHAR* '"""';

DECIMAL_INTEGER: [1-9] [0-9]*
                | '0'+
                ;
HEX_NUMBER: '0' [xX] HEX_DIGIT+;
OCT_NUMBER: '0' [oO] OCT_DIGIT+;
BIN_NUMBER: '0' [bB] BIN_DIGIT+;
FLOAT_NUMBER: EXPONENT_OR_POINT_FLOAT;