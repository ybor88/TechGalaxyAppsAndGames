# Copyright (c) Roberto Di Flumeri
from .models import Program, OPERATIONS, VarType, VALUE_SOURCE_USER


def _literal(value) -> str:
    return repr(value)


def _collect_imports(program: Program):
    imports = set()
    for operation in program.operations:
        needs = OPERATIONS[operation.op_id].get("needs_import")
        if needs:
            imports.add(needs)
    return sorted(imports)


def _operation_expr(operation) -> str:
    spec = OPERATIONS[operation.op_id]
    if spec["kind"] == "call":
        return f"{spec['call']}({operation.operand_names[0]})"
    symbol = spec["symbol"]
    return f" {symbol} ".join(operation.operand_names)


def _console_input_lines(var) -> list:
    prompt = f"Inserisci {var.name}: "
    if var.type is VarType.INT:
        return [f'{var.name} = int(input({prompt!r}))']
    if var.type is VarType.FLOAT:
        return [f'{var.name} = float(input({prompt!r}).replace(",", "."))']
    if var.type is VarType.LIST:
        list_prompt = f"Inserisci {var.name} (numeri separati da virgola): "
        return [
            f'{var.name}_raw = input({list_prompt!r})',
            f'{var.name} = [float(v) if "." in v else int(v) for v in {var.name}_raw.split(",")]',
        ]
    return [f'{var.name} = input({prompt!r})']


def generate_console(program: Program) -> str:
    lines = ["# Programma generato da Vanta", "# Copyright (c) Roberto Di Flumeri"]

    for module in _collect_imports(program):
        lines.append(f"import {module}")
    lines.append("")

    for var in program.variables:
        if var.source == VALUE_SOURCE_USER:
            lines.extend(_console_input_lines(var))
        else:
            lines.append(f"{var.name} = {_literal(var.value)}")

    if program.variables:
        lines.append("")

    for operation in program.operations:
        expr = _operation_expr(operation)
        lines.append(f"{operation.result_name} = {expr}")
        lines.append(f'print(f"{operation.result_name} = {{{operation.result_name}}}")')
        lines.append("")

    return "\n".join(lines).rstrip() + "\n"


def _gui_cast_expr(var, raw_expr: str) -> str:
    if var.type is VarType.INT:
        return f"int({raw_expr})"
    if var.type is VarType.FLOAT:
        return f'float({raw_expr}.replace(",", "."))'
    if var.type is VarType.LIST:
        return f'[float(v) if "." in v else int(v) for v in {raw_expr}.split(",")]'
    return raw_expr


def generate_gui(program: Program) -> str:
    lines = [
        "# Programma generato da Vanta - interfaccia grafica",
        "# Copyright (c) Roberto Di Flumeri",
    ]
    for module in _collect_imports(program):
        lines.append(f"import {module}")
    lines.append("import tkinter as tk")
    lines.append("from tkinter import messagebox")
    lines.append("")
    lines.append('root = tk.Tk()')
    lines.append('root.title("Programma Vanta")')
    lines.append('root.configure(bg="#07070d")')
    lines.append("")
    lines.append("entries = {}")
    lines.append("result_vars = {}")
    lines.append("")
    lines.append('LABEL_STYLE = dict(bg="#07070d", fg="#eef0ff", font=("Segoe UI", 11))')
    lines.append('ENTRY_STYLE = dict(bg="#14142a", fg="#eef0ff", insertbackground="#eef0ff")')
    lines.append("")
    lines.append("row = 0")

    user_vars = [v for v in program.variables if v.source == VALUE_SOURCE_USER]
    fixed_vars = [v for v in program.variables if v.source != VALUE_SOURCE_USER]

    for var in fixed_vars:
        label_text = f"{var.name} = {var.value}"
        lines.append(f'tk.Label(root, text={label_text!r}, **LABEL_STYLE)'
                      '.grid(row=row, column=0, columnspan=2, sticky="w", padx=12, pady=4)')
        lines.append("row += 1")

    for var in user_vars:
        lines.append(f'tk.Label(root, text="{var.name}:", **LABEL_STYLE)'
                      '.grid(row=row, column=0, sticky="w", padx=12, pady=4)')
        lines.append(f'entries["{var.name}"] = tk.Entry(root, **ENTRY_STYLE)')
        lines.append(f'entries["{var.name}"].grid(row=row, column=1, padx=12, pady=4)')
        lines.append("row += 1")

    for operation in program.operations:
        lines.append(f'result_vars["{operation.result_name}"] = tk.StringVar(value="{operation.result_name} = ?")')

    lines.append("")
    lines.append("def calcola():")
    body = []
    for var in fixed_vars:
        body.append(f"{var.name} = {_literal(var.value)}")
    for var in user_vars:
        raw_expr = f'entries["{var.name}"].get()'
        cast_expr = _gui_cast_expr(var, raw_expr)
        body.append(f"try:")
        body.append(f"    {var.name} = {cast_expr}")
        body.append(f"except ValueError:")
        body.append(f'    messagebox.showerror("Errore", "Valore non valido per {var.name}")')
        body.append(f"    return")
    for operation in program.operations:
        expr = _operation_expr(operation)
        body.append(f"{operation.result_name} = {expr}")
        body.append(
            f'result_vars["{operation.result_name}"].set(f"{operation.result_name} = {{{operation.result_name}}}")'
        )
    if not body:
        body.append("pass")
    for line in body:
        lines.append("    " + line)

    lines.append("")
    lines.append('tk.Button(root, text="Calcola", command=calcola, bg="#8a3ffc", fg="white",')
    lines.append('          font=("Segoe UI", 11, "bold"), relief="flat", padx=16, pady=8)'
                 '.grid(row=row, column=0, columnspan=2, pady=12)')
    lines.append("row += 1")
    lines.append("")
    for operation in program.operations:
        lines.append(f'tk.Label(root, textvariable=result_vars["{operation.result_name}"], **LABEL_STYLE)'
                      '.grid(row=row, column=0, columnspan=2, sticky="w", padx=12, pady=4)')
        lines.append("row += 1")

    lines.append("")
    lines.append("root.mainloop()")

    return "\n".join(lines) + "\n"


def generate_source(program: Program, mode: str = "console") -> str:
    if mode == "gui":
        return generate_gui(program)
    return generate_console(program)
