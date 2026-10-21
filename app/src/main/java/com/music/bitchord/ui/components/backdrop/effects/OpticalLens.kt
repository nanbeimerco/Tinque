/*
 * Mornye's glass lens, for the vendored Kyant0/backdrop pipeline.
 *
 * Mornye (Flutter) draws its glass with liquid_glass_easy 5.0.0
 * (github.com/AhmeedGamil/liquid_glass_easy, Copyright © 2025 Ahmed Gamil,
 * "Free to use in any project; credit is appreciated"). The refraction below
 * follows that package's `computeRefractedPosition`: inside a band along the
 * edge the 2D outline normal is tilted into a 3D bevel normal, a ray straight
 * into the screen is refracted through it, and the view shifts by the ray's
 * screen-space part times a fixed travel (70 logical px) that fades to nothing
 * at the band's inner end. The colour split follows its `dispersionOffsetUv`
 * (a diagonal, brightness-scaled offset, off below 0.66 px). The rim light is
 * a re-implementation of its idea — the backdrop seen through the edge,
 * lifted, on two arcs facing the light — not a copy of its code.
 */
package com.music.bitchord.ui.components.backdrop.effects

import androidx.compose.ui.util.fastCoerceAtLeast
import com.music.bitchord.ui.components.backdrop.BackdropEffectScope
import com.music.bitchord.ui.components.backdrop.internal.RuntimeShaderEffect
import com.music.bitchord.ui.components.backdrop.isRuntimeShaderSupported
import org.intellij.lang.annotations.Language
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** One light on the rim: a band [thickness] px deep, on arcs facing [angleDegrees]. */
class RimLight(
    val thickness: Float,
    val angleDegrees: Float,
    /** 0 = focused on the light's axis, 1 = wrapped round the whole outline. */
    val spread: Float,
    val intensity: Float,
    /** Light colour, and how much (alpha) it replaces the lifted backdrop colour. */
    val red: Float = 1f,
    val green: Float = 1f,
    val blue: Float = 1f,
    val colorAlpha: Float = 0f,
)

/**
 * Bends the backdrop along the shape's rim the way Mornye's glass does.
 *
 * @param band refraction width: how far in from the edge the bending reaches (px).
 * @param refractionIndex 1 = no bending; Mornye's bars sit around 1.07–1.17.
 * @param travel the fixed displacement scale (Mornye: 70 logical px), in px.
 * @param dispersion widest colour split at the edge (px); [dispersionMin] and below draws none.
 * @param specular the sharp rim light, or null.
 * @param glow the soft side band, or null.
 */
fun BackdropEffectScope.opticalLens(
    band: Float,
    refractionIndex: Float,
    travel: Float,
    dispersion: Float = 0f,
    dispersionMin: Float = 0f,
    specular: RimLight? = null,
    glow: RimLight? = null,
) {
    if (!isRuntimeShaderSupported()) return
    if (band <= 0f) return
    val radii = cornerRadii ?: return

    if (padding > 0f) {
        padding = (padding - band).fastCoerceAtLeast(0f)
    }

    val shader = obtainRuntimeShader("ClearGlassLens", ClearLensShaderString).apply {
        setFloatUniform("size", size.width, size.height)
        setFloatUniform("offset", -padding, -padding)
        setFloatUniform("cornerRadii", radii)
        setFloatUniform("band", band)
        setFloatUniform("eta", 1f / refractionIndex.coerceAtLeast(1f))
        setFloatUniform("travel", travel)
        setFloatUniform("dispersion", dispersion, dispersionMin)
        setRim("specular", specular)
        setRim("glow", glow)
    }
    effect(RuntimeShaderEffect(shader, "content"))
}

private fun com.music.bitchord.ui.components.backdrop.RuntimeShader.setRim(name: String, light: RimLight?) {
    if (light == null || light.intensity <= 0f || light.thickness <= 0f) {
        setFloatUniform("${name}Shape", 1f, 0f, 0f, 1f)
        setFloatUniform("${name}Color", 1f, 1f, 1f, 0f)
        setFloatUniform("${name}Power", 1f)
        return
    }
    val angle = light.angleDegrees * (PI / 180.0).toFloat()
    setFloatUniform("${name}Shape", cos(angle), -sin(angle), light.intensity, light.thickness)
    setFloatUniform("${name}Color", light.red, light.green, light.blue, light.colorAlpha)
    // Spread 0 → a tight arc (high power), 1 → almost even all round.
    setFloatUniform("${name}Power", (1f - light.spread.coerceIn(0f, 1f)).pow(2) * 12f + 0.35f)
}

/** Mornye's index from its refraction depth and width (see MornyeLiquidBackdrop). */
fun clearRefractionIndex(refractionWidth: Float, refractionDepth: Float): Float {
    val edgeBend = sqrt(1f - 1f / (1.7f * 1.7f)) * refractionWidth * refractionDepth * 5f / 70f
    val bend = edgeBend.coerceIn(0f, 0.95f)
    return 1f / sqrt(1f - bend * bend)
}

@Language("AGSL")
private const val ClearLensShaderString = """
uniform shader content;

uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float band;
uniform float eta;
uniform float travel;
uniform float2 dispersion;

// x, y = light direction; z = intensity; w = thickness (px)
uniform float4 specularShape;
uniform float4 specularColor;
uniform float specularPower;
uniform float4 glowShape;
uniform float4 glowColor;
uniform float glowPower;

float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
    }
}

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}

// The backdrop seen through the edge, lifted toward white with its hue kept.
float3 lift(float3 c) {
    return c + (1.0 - c) * 0.55;
}

float rimWeight(float inside, float2 normal, float4 shape, float power) {
    if (shape.z <= 0.0) return 0.0;
    // A hard core on the edge with a soft tail inward.
    float t = clamp(inside / max(shape.w, 0.0001), 0.0, 1.0);
    float ramp = (1.0 - t) * (1.0 - t);
    float facing = dot(normal, shape.xy);
    float arc = pow(max(facing * facing, 0.0001), power);
    return clamp(shape.z * ramp * arc, 0.0, 1.0);
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centered = (coord + offset) - halfSize;
    float radius = radiusAt(centered, cornerRadii);
    float inside = -sdRoundedRect(centered, halfSize, radius);
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 normal = normalize(gradSdRoundedRect(centered, halfSize, gradRadius));

    half4 color = half4(0.0);
    if (inside >= band) {
        // The middle of the pane is not bent at all.
        color = content.eval(coord);
    } else {
        float h = clamp(inside / band, 0.0, 1.0);
        float zoneT = 1.0 - h;
        float bulge = sqrt(max(1.0 - h * h, 0.0));
        float3 n3 = normalize(float3(normal * bulge, h));
        float3 r = refract(float3(0.0, 0.0, -1.0), n3, eta);
        float2 refracted = coord + r.xy * (travel * zoneT);
        color = content.eval(refracted);

        float shift = dispersion.x * zoneT;
        if (shift >= dispersion.y && shift > 0.0) {
            float luma = dot(float3(color.rgb), float3(0.2126, 0.7152, 0.0722));
            float2 split = float2(shift * luma);
            color.r = content.eval(refracted + split).r;
            color.b = content.eval(refracted - split).b;
        }
    }

    // Rim light over the bent backdrop (the glass tint is painted over all of it).
    float3 base = float3(color.rgb);
    float ws = rimWeight(inside, normal, specularShape, specularPower);
    if (ws > 0.0) {
        float3 c = mix(lift(base), specularColor.rgb, specularColor.a);
        base = mix(base, c, ws);
    }
    float wg = rimWeight(inside, normal, glowShape, glowPower) * 0.7;
    if (wg > 0.0) {
        float3 c = mix(lift(base), glowColor.rgb, glowColor.a);
        base = mix(base, c, wg);
    }
    return half4(half3(base), color.a);
}"""
