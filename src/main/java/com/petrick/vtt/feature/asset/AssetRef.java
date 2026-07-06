package com.petrick.vtt.feature.asset;

/**
 * Referência para um asset usado pelo VTT.
 *
 * Por enquanto é uma abstração simples.
 * Futuramente poderá representar:
 * - textura interna do mod
 * - arquivo local importado
 * - textura em cache
 * - asset sincronizado pelo servidor
 */
public sealed interface AssetRef permits BuiltInTextureAssetRef {

    String id();

}