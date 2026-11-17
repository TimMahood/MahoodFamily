package com.mahood.family.core

import com.mahood.family.core.shopping.Aisles
import com.mahood.family.core.shopping.Quantity
import org.junit.Assert.assertEquals
import org.junit.Test

class AislesTest {
    private fun check(vararg cases: Pair<String, String>) {
        val wrong = cases.filter { (name, aisle) -> Aisles.guess(name) != aisle }
            .map { (name, aisle) -> "$name: expected $aisle, got ${Aisles.guess(name)}" }
        assertEquals(emptyList<String>(), wrong)
    }

    @Test
    fun wordsNotSubstrings() = check(
        "Toothpaste" to "Toiletries & health",
        "Shampoo" to "Toiletries & health",
        "Pineapple" to "Fruit & veg",
        "Graham crackers" to "Cupboard",
        "Hamburger buns" to "Bakery",
    )

    @Test
    fun everydayShop() = check(
        "Bananas" to "Fruit & veg",
        "Broccoli" to "Fruit & veg",
        "Tomatoes" to "Fruit & veg",
        "Strawberries" to "Fruit & veg",
        "Oat milk" to "Dairy & eggs",
        "Cheddar" to "Dairy & eggs",
        "Eggs" to "Dairy & eggs",
        "Sourdough loaf" to "Bakery",
        "Pasta" to "Cupboard",
        "Coffee beans" to "Cupboard",
        "Chicken breasts" to "Meat & fish",
        "Mince" to "Meat & fish",
        "Fish fingers" to "Frozen",
        "Frozen peas" to "Frozen",
        "Ice cream" to "Frozen",
        "Orange juice" to "Drinks",
        "Peanut butter" to "Cupboard",
        "Chicken soup" to "Cupboard",
        "Chicken stock cubes" to "Cupboard",
        "Coconut milk" to "Cupboard",
        "Nappies" to "Baby",
        "Baby wipes" to "Baby",
        "Calpol" to "Toiletries & health",
        "Toilet roll" to "Household",
        "Kitchen roll" to "Household",
        "Dishwasher tablets" to "Household",
        "Antibacterial wipes" to "Household",
        "Bottle of wine" to "Drinks",
        "Batteries" to "Household",
        "Something odd" to "Other",
    )

    @Test
    fun learnedCorrectionWins() {
        assertEquals("Cupboard", Aisles.guess("Pesto", mapOf("pesto" to "Cupboard")))
        assertEquals("Baby", Aisles.guess("Ella's yogurts", mapOf("ella s yogurts" to "Baby")))
    }

    @Test
    fun quantities() {
        assertEquals("2" to "milk", Quantity.parse("2 milk"))
        assertEquals("2" to "milk", Quantity.parse("2x milk"))
        assertEquals("3" to "milk", Quantity.parse("milk x3"))
        assertEquals("500g" to "mince", Quantity.parse("500g mince"))
        assertEquals("2 packs" to "nappies", Quantity.parse("2 packs of nappies"))
        assertEquals(null to "Bananas", Quantity.parse("Bananas"))
        assertEquals("1" to "lemon", Quantity.parse("1 lemon"))
    }
}
