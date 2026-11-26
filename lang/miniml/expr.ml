(* 
                         CS 51 Final Project
                        MiniML -- Expressions
*)

(*......................................................................
  Abstract syntax of MiniML expressions 
 *)

type unop =
  | Negate
;;

let abst_string_of_unop : unop -> string =
  function
  | Negate -> "negate"
;;

let string_of_unop : unop -> string = 
  function
  | Negate -> "-"

type binop =
  | Plus
  | Minus
  | Times
  | Equals
  | LessThan
;;

let abst_string_of_binop : binop -> string =
  function
  | Plus -> "plus"
  | Minus -> "minus"
  | Times -> "times"
  | Equals -> "equals"
  | LessThan -> "less_than"
;;

let string_of_binop : binop -> string =
  function
  | Plus -> "+"
  | Minus -> "-"
  | Times -> "*"
  | Equals -> "="
  | LessThan -> "<"
;;

type varid = string ;;
  
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
module SS = Set.Make (struct
                       type t = varid
                       let compare = String.compare
                     end ) ;;

type varidset = SS.t ;;

(* same_vars varids1 varids2 -- Tests to see if two `varid` sets have
   the same elements (for testing purposes) *)
let same_vars : varidset -> varidset -> bool =
  SS.equal;;

(* vars_of_list varids -- Generates a set of variable names from a
   list of `varid`s (for testing purposes) *)
let vars_of_list : string list -> varidset =
  SS.of_list ;;
  
(* free_vars exp -- Returns the set of `varid`s corresponding to free
   variables in `exp` *)
let free_vars : expr -> varidset = let open SS in
  let set = empty in
  let rec free_vars_given (unbound : varidset) (bound : varidset) : expr -> varidset =
    let pass ?(bound : varidset = empty) : expr -> varidset = free_vars_given unbound bound in
    function
    | Var id -> add id unbound
    | Int _n -> unbound
    | Bool _b -> unbound
    | Unop (_u, e) -> pass e
    | Binop (_b, e1, e2) -> pass e1 |> union (pass e2)
    | Conditional (cond, et, ef) -> pass et |> union (pass ef) |> union (pass cond)
    | Fun (id, e) -> pass ~bound:(add id bound) e
    | Let (id, ev, e) -> pass ev |> union (pass ~bound:(add id bound) e)
    | Letrec (id, ev, e) -> pass ~bound:(add id bound) ev |> union (pass ~bound:(add id bound) e)
    | Raise -> unbound
    | Unassigned -> unbound
    | App (ef, ea) -> pass ef |> union (pass ea)
  in free_vars_given set empty
;;
  
(* new_varname () -- Returns a freshly minted `varid` with prefix
   "var" and a running counter a la `gensym`. Assumes no other
   variable names use the prefix "var". (Otherwise, they might
   accidentally be the same as a generated variable name.) *)
let new_varname : unit -> varid =
  let gensym = ref 0 in
  fun () -> decr gensym ; "var" ^ string_of_int !gensym
;;

(*......................................................................
  Substitution 

  Substitution of expressions for free occurrences of variables is the
  cornerstone of the substitution model for functional programming
  semantics.
 *)

(* subst var_name repl exp -- Return the expression `exp` with `repl`
   substituted for free occurrences of `var_name`, avoiding variable
   capture *)
let rec subst (var_name : varid) (repl : expr) (exp : expr) : expr =
  let pass : expr -> expr = subst var_name repl in
  match exp with
  | Var id -> if var_name = id then repl else Var id
  | Int _ -> exp
  | Bool _ -> exp
  | Unop (u, e) -> Unop (u, pass e)
  | Binop (b, e1, e2) -> Binop (b, pass e1, pass e2)
  | Conditional (cond, et, ef) -> Conditional (pass cond, pass et, pass ef)
  | Fun (id, e) -> if var_name = id then exp else Fun (id, pass e)
  | Let (id, ev, e) -> if var_name = id then Let (id, pass ev, e) else Let (id, pass ev, pass e)
  | Letrec (id, ev, e) -> if var_name = id then exp else Let (id, pass ev, pass e)
  | Raise -> exp
  | Unassigned -> exp
  | App (ef, ea) -> App (pass ef, pass ea)
;;
     
(*......................................................................
  String representations of expressions
 *)
   
(* exp_to_concrete_string exp -- Returns a string representation of
   the concrete syntax of the expression `exp` *)
let rec exp_to_concrete_string : expr -> string =
  function
  | Var id -> id
  | Int n -> string_of_int n
  | Bool b -> string_of_bool b
  | Unop (u, e) -> string_of_unop u ^ exp_to_concrete_string e
  | Binop (b, e1, e2) ->
    exp_to_concrete_string e1 ^ abst_string_of_binop b ^ exp_to_concrete_string e2
  | Conditional (cond, et, ef) ->
    "if " ^ exp_to_concrete_string cond ^ " then " ^ exp_to_concrete_string et ^ " else " ^ exp_to_concrete_string ef
  | Fun (id, e) -> "fun " ^ id ^ " -> " ^ exp_to_concrete_string e
  | Let (id, ev, e) ->
    "let " ^ id ^ " = " ^ exp_to_concrete_string ev ^ " in " ^ exp_to_concrete_string e
  | Letrec (id, ev, e) ->
    "let rec " ^ id ^ " = " ^ exp_to_concrete_string ev ^ " in " ^ exp_to_concrete_string e
  | Raise -> "raise"
  | Unassigned -> "unassigned"
  | App (ef, ea) -> exp_to_concrete_string ef ^ " " ^ exp_to_concrete_string ea
;;

(* exp_to_abstract_string exp -- Return a string representation of the
   abstract syntax of the expression `exp` *)
let rec exp_to_abstract_string : expr -> string =
  function
  | Var id -> "Var(" ^ id ^ ")"
  | Int n -> "Int(" ^ string_of_int n ^ ")"
  | Bool b -> "Bool(" ^ string_of_bool b ^ ")"
  | Unop (u, e) -> "Unop(" ^ abst_string_of_unop u ^ ", " ^ exp_to_abstract_string e ^ ")"
  | Binop (b, e1, e2) ->
    "Binop(" ^ abst_string_of_binop b ^ ", " ^ exp_to_abstract_string e1 ^ ", " ^ exp_to_abstract_string e2 ^ ")"
  | Conditional (cond, et, ef) ->
    "Cond(" ^ exp_to_abstract_string cond ^ ", " ^ exp_to_abstract_string et ^ ", " ^ exp_to_abstract_string ef ^ ")"
  | Fun (id, e) -> "Fun(" ^ id ^ ", " ^ exp_to_abstract_string e ^ ")"
  | Let (id, ev, e) ->
    "Let(" ^ id ^ ", " ^ exp_to_abstract_string ev ^ ", " ^ exp_to_abstract_string e ^ ")"
  | Letrec (id, ev, e) ->
    "Letrec(" ^ id ^ ", " ^ exp_to_abstract_string ev ^ ", " ^ exp_to_abstract_string e ^ ")"
  | Raise -> "Raise"
  | Unassigned -> "Unassigned"
  | App (ef, ea) -> "App(" ^ exp_to_abstract_string ef ^ ", " ^ exp_to_abstract_string ea ^ ")"
;;
