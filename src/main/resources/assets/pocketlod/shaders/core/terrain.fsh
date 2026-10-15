#version 150
#moj_import <fog.glsl>
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform vec2 NearCenter;
uniform float NearHalfWidth;
in float vertexDistance;
in vec4 vertexColor;
in vec3 localBlockPos;
in vec3 worldBlockPos;
out vec4 fragColor;
float hash(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.x + p.y) * p.z);
}
void main() {
    vec2 delta = localBlockPos.xz - NearCenter;
    float squared = dot(delta, delta);
    float inner = NearHalfWidth - 3.5;
    if (squared < inner * inner) discard;
    if (squared < NearHalfWidth * NearHalfWidth) {
        float coverage = smoothstep(inner, NearHalfWidth, sqrt(squared));
        float dither = fract(dot(floor(gl_FragCoord.xy), vec2(0.754877666, 0.569840296)));
        if (dither > coverage) discard;
    }
    vec4 color = vertexColor * ColorModulator;
    vec3 cell = floor(worldBlockPos + vec3(0.002));
    float grain = (hash(cell) * 2.0 - 1.0) * 0.034;
    color.rgb *= 1.0 + grain * (1.0 - smoothstep(48.0, 380.0, vertexDistance));
    float haze = smoothstep(32.0, max(FogEnd, 33.0), vertexDistance) * 0.42;
    color.rgb = mix(color.rgb, FogColor.rgb, haze);
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
