attribute vec2 p;
attribute vec2 t;
varying vec2 v;
void main() { v=t; gl_Position=vec4(p,0.0,1.0); }
