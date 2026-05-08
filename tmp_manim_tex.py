from manim import *

class SmokeTex(Scene):
    def construct(self):
        eq = MathTex(r"|x-1|+|x-3|=2")
        self.add(eq)
        self.wait(0.1)
