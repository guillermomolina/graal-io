grammar IOLanguage_;

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
arguments: OPEN EOL* (expressionList (EOL* COMMA EOL* expressionList?)*)? EOL* CLOSE;

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

EOL: [\r\n];
WS: [ \t\u000C]+ -> skip;
COMMENT: '/*' .*? '*/' -> skip;
LINE_COMMENT: ('//' | '#') ~[\r\n]* -> skip;

fragment LETTER: [A-Z] | [a-z];
fragment NON_ZERO_DIGIT: [1-9];
fragment DIGIT: [0-9];
fragment HEX_DIGIT: [0-9] | [a-f] | [A-F];
fragment OCT_DIGIT: [0-7];
fragment BINARY_DIGIT: '0' | '1';
fragment TAB: '\t';
fragment STRING_CHAR: ~('"' | '\r' | '\n');

IDENTIFIER: (LETTER | '_') (LETTER | '_' | DIGIT)*;

COMMA: ',';
OPEN: '(' | '[' | '{';
CLOSE:')' | ']' | '}';

OPERATOR:  (':' | '.' | '\'' | '~' | '!' | '@' | '$' | 
    '%' | '^' | '&' | '*' | '-' | '+' | '/' | '=' | '{' | '}' | 
    '[' | ']' | '|' | '\\' | '<' | '>' | '?')+;


STRING: MONOQUOTE | TRIQUOTE;
MONOQUOTE: '"' STRING_CHAR* '"';
TRIQUOTE: '"""' STRING_CHAR* '"""';
NUMBER: '0' | NON_ZERO_DIGIT DIGIT*;
