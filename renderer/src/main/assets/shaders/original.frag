precision mediump float;
varying vec2 v;
uniform sampler2D tex;
void main() { gl_FragColor=vec4(texture2D(tex,v).rgb,1.0); }
