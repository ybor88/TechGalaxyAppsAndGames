# Copyright (c) Roberto Di Flumeri
from pathlib import Path

from PySide6.QtGui import QIcon
from PySide6.QtWidgets import QMainWindow, QStackedWidget

from .models import Program
from .steps import (
    WelcomeStep, OutputModeStep, VariableCountStep, VariableDetailStep,
    OperationChoiceStep, OperandChoiceStep, ResultNameStep, ResultStep,
)


class MainWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("Vanta")
        self.resize(980, 720)
        self.setMinimumSize(760, 560)

        self.logo_path = Path(__file__).resolve().parent.parent / "assets" / "logo.png"
        if self.logo_path.exists():
            self.setWindowIcon(QIcon(str(self.logo_path)))

        self.program = Program()
        self.variable_total = 0
        self.output_mode = "console"

        self.stack = QStackedWidget()
        self.setCentralWidget(self.stack)

        self.show_welcome()

    def navigate_to(self, widget):
        old = self.stack.currentWidget()
        self.stack.addWidget(widget)
        self.stack.setCurrentWidget(widget)
        if old is not None:
            self.stack.removeWidget(old)
            old.deleteLater()

    def show_welcome(self):
        self.program = Program()
        self.variable_total = 0
        self.output_mode = "console"
        self.navigate_to(WelcomeStep(self))

    def show_output_mode(self):
        self.navigate_to(OutputModeStep(self))

    def show_variable_count(self):
        self.navigate_to(VariableCountStep(self))

    def start_variable_definitions(self, count):
        self.variable_total = count
        self.program.variables = []
        self.show_variable_detail(0, count)

    def show_variable_detail(self, index, total):
        del self.program.variables[index:]
        self.navigate_to(VariableDetailStep(self, index, total))

    def show_operation_choice(self):
        self.navigate_to(OperationChoiceStep(self))

    def show_operand_choice(self, op_id):
        self.navigate_to(OperandChoiceStep(self, op_id))

    def show_result_name(self, op_id, operand_names):
        self.navigate_to(ResultNameStep(self, op_id, operand_names))

    def show_result(self):
        self.navigate_to(ResultStep(self))

    def reset(self):
        self.show_welcome()
