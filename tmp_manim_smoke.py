from manim import *

class Smoke(Scene):
    def construct(self):
        t = Text("smoke")
        self.add(t)
        self.wait(0.1)
