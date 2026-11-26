(* 
                         CS 51 Final Project
                        MiniML -- Expressions
*)

(* ......................................................................
  Abstract syntax of MiniML expressions 
 *)

(* Unary operators *)
type unop =
  | Negate
;;

(* Binary operators *)
type binop =
  | Plus
  | Minus
  | Times
  | Equals
  | LessThan
;;

(* Variable identifers *)
type varid = string ;;
  
(* Expressions *)
type expr =
  | Var of varid                         (* variables *)
  | Int of int                           (* integers *)
  | Bool of bool                         (* booleans *)
  | Unop of unop * expr                  (* unary operators *)
  | Binop of binop * expr * expr         (* binary operators *)
  | Conditional of expr * expr * expr    (* if then else *)
  | Fun of varid * expr                  (* function definitions *)
  | Let of varid * expr * expr           (* local naming *)
  | Letrec of varid * expr * expr        (* recursive local naming *)
  | Raise                                (* exceptions *)
  | Unassigned                           (* (temporarily) unassigned *)
  | App of expr * expr                   (* function applications *)
;;
    
(*......................................................................
  Manipulation of variable names (varids) and sets of them
 *)

(* varidset -- Sets of varids *)
type varidset ;;

(* same_vars varids1 varids2 -- Tests to see if two `varid` sets have
   the same elements (for testing purposes) *)
val same_vars : varidset -> varidset -> bool ;;

(* vars_of_list varids -- Generates a set of variable names from a
   list of `varid`s (for testing purposes) *)
val vars_of_list : varid list -> varidset ;;

(* free_vars exp -- Returns the set of `varid`s corresponding to free
   variables in `exp` *)
val free_vars : expr -> varidset ;;

(* new_varname () -- Returns a freshly minted `varid` *)
val new_varname : unit -> varid ;;

(*......................................................................
  Substitution 

  Substitution of expressions for free occurrences of variables is the
  cornerstone of the substitution model for functional programming
  semantics.
 *)
                            
(* subst var_name repl exp -- Return the expression `exp` with `repl`
   substituted for free occurrences of `var_name`, avoiding variable
   capture *)
val subst : varid -> expr -> expr -> expr ;;
     
(*......................................................................
  String representations of expressions
 *)

(* exp_to_concrete_string exp -- Returns a string representation of
   the concrete syntax of the expression `exp` *)
val exp_to_concrete_string : expr -> string ;;

(* exp_to_abstract_string exp -- Return a string representation of the
   abstract syntax of the expression `exp` *)
val exp_to_abstract_string : expr -> string ;;

