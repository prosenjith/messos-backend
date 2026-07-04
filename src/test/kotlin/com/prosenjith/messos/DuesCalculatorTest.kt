package com.prosenjith.messos

import com.prosenjith.messos.util.DepositEntry
import com.prosenjith.messos.util.DuesCalculator
import com.prosenjith.messos.util.ExpenseEntry
import com.prosenjith.messos.util.MealEntry
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DuesCalculatorTest {

    private val alice = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val bob   = UUID.fromString("00000000-0000-0000-0000-000000000002")

    @Test
    fun `balanced cycle - each member owes exactly what they deposited`() {
        // total meals = 5, total expenses = 500, mealRate = 100
        val meals = listOf(
            MealEntry(alice, breakfastCount = 1.0, lunchCount = 1.0, dinnerCount = 1.0), // 3
            MealEntry(bob,   breakfastCount = 1.0, lunchCount = 1.0, dinnerCount = 0.0), // 2
        )
        val expenses = listOf(ExpenseEntry(500.0))
        val deposits = listOf(DepositEntry(alice, 300.0), DepositEntry(bob, 200.0))

        val result = DuesCalculator.calculate(meals, expenses, deposits)

        assertEquals(100.0, result.mealRate)
        assertEquals(500.0, result.totalExpenses)
        assertEquals(5.0,   result.totalMeals)

        val a = result.balances.first { it.memberUserId == alice }
        assertEquals(3.0,   a.totalMeals)
        assertEquals(300.0, a.mealCost)
        assertEquals(300.0, a.totalDeposited)
        assertEquals(0.0,   a.balance)

        val b = result.balances.first { it.memberUserId == bob }
        assertEquals(2.0,   b.totalMeals)
        assertEquals(200.0, b.mealCost)
        assertEquals(200.0, b.totalDeposited)
        assertEquals(0.0,   b.balance)
    }

    @Test
    fun `member with surplus has positive balance`() {
        // 2 meals, 200 expenses → rate = 100; deposited 500 → surplus = +300
        val meals    = listOf(MealEntry(alice, 1.0, 1.0, 0.0))
        val expenses = listOf(ExpenseEntry(200.0))
        val deposits = listOf(DepositEntry(alice, 500.0))

        val result = DuesCalculator.calculate(meals, expenses, deposits)
        val a = result.balances.first { it.memberUserId == alice }

        assertEquals(100.0,  result.mealRate)
        assertEquals(200.0,  a.mealCost)
        assertEquals(500.0,  a.totalDeposited)
        assertEquals(300.0,  a.balance)
    }

    @Test
    fun `member with deficit has negative balance`() {
        // 3 meals, 300 expenses → rate = 100; deposited only 100 → deficit = -200
        val meals    = listOf(MealEntry(alice, 1.0, 1.0, 1.0))
        val expenses = listOf(ExpenseEntry(300.0))
        val deposits = listOf(DepositEntry(alice, 100.0))

        val result = DuesCalculator.calculate(meals, expenses, deposits)
        val a = result.balances.first { it.memberUserId == alice }

        assertEquals(300.0,  a.mealCost)
        assertEquals(100.0,  a.totalDeposited)
        assertEquals(-200.0, a.balance)
    }

    @Test
    fun `zero total meals gives zero meal rate and no meal cost`() {
        val expenses = listOf(ExpenseEntry(1000.0))
        val deposits = listOf(DepositEntry(alice, 500.0))

        val result = DuesCalculator.calculate(emptyList(), expenses, deposits)

        assertEquals(0.0,   result.mealRate)
        assertEquals(0.0,   result.totalMeals)

        val a = result.balances.first { it.memberUserId == alice }
        assertEquals(0.0,   a.mealCost)
        assertEquals(500.0, a.balance)
    }

    @Test
    fun `zero expenses gives zero meal rate and balance equals deposits`() {
        val meals    = listOf(MealEntry(alice, 1.0, 1.0, 1.0))
        val deposits = listOf(DepositEntry(alice, 200.0))

        val result = DuesCalculator.calculate(meals, emptyList(), deposits)

        assertEquals(0.0,   result.mealRate)
        val a = result.balances.first { it.memberUserId == alice }
        assertEquals(0.0,   a.mealCost)
        assertEquals(200.0, a.balance)
    }

    @Test
    fun `member with deposits but no meals has positive balance equal to deposit`() {
        // bob deposited but never ate
        val meals    = listOf(MealEntry(alice, 1.0, 1.0, 1.0))
        val expenses = listOf(ExpenseEntry(300.0))
        val deposits = listOf(DepositEntry(alice, 300.0), DepositEntry(bob, 150.0))

        val result = DuesCalculator.calculate(meals, expenses, deposits)
        val b = result.balances.first { it.memberUserId == bob }

        assertEquals(0.0,   b.totalMeals)
        assertEquals(0.0,   b.mealCost)
        assertEquals(150.0, b.totalDeposited)
        assertEquals(150.0, b.balance)
    }

    @Test
    fun `member with meals but no deposits has negative balance equal to meal cost`() {
        // alice ate but didn't pay
        val meals    = listOf(MealEntry(alice, 1.0, 1.0, 1.0))
        val expenses = listOf(ExpenseEntry(300.0))

        val result = DuesCalculator.calculate(meals, emptyList<ExpenseEntry>().plus(expenses), emptyList())
        val a = result.balances.first { it.memberUserId == alice }

        assertEquals(0.0,    a.totalDeposited)
        assertEquals(300.0,  a.mealCost)
        assertEquals(-300.0, a.balance)
    }

    @Test
    fun `multiple deposits for same member are summed`() {
        // 2 meals, 400 expenses → rate = 200; 3 × 100 deposits = 300 → balance = -100
        val meals    = listOf(MealEntry(alice, 1.0, 1.0, 0.0))
        val expenses = listOf(ExpenseEntry(400.0))
        val deposits = listOf(
            DepositEntry(alice, 100.0),
            DepositEntry(alice, 100.0),
            DepositEntry(alice, 100.0)
        )

        val result = DuesCalculator.calculate(meals, expenses, deposits)
        val a = result.balances.first { it.memberUserId == alice }

        assertEquals(300.0,  a.totalDeposited)
        assertEquals(400.0,  a.mealCost)
        assertEquals(-100.0, a.balance)
    }

    @Test
    fun `multiple expense entries are summed`() {
        val meals    = listOf(MealEntry(alice, 1.0, 0.0, 0.0))
        val expenses = listOf(ExpenseEntry(100.0), ExpenseEntry(200.0), ExpenseEntry(300.0))
        val deposits = listOf(DepositEntry(alice, 600.0))

        val result = DuesCalculator.calculate(meals, expenses, deposits)

        assertEquals(600.0, result.totalExpenses)
        assertEquals(600.0, result.mealRate)
        val a = result.balances.first { it.memberUserId == alice }
        assertEquals(600.0, a.mealCost)
        assertEquals(0.0,   a.balance)
    }

    @Test
    fun `all empty inputs returns empty result`() {
        val result = DuesCalculator.calculate(emptyList(), emptyList(), emptyList())

        assertEquals(0.0, result.mealRate)
        assertEquals(0.0, result.totalExpenses)
        assertEquals(0.0, result.totalMeals)
        assertTrue(result.balances.isEmpty())
    }

    @Test
    fun `half meal counts produce fractional meal cost`() {
        // alice: 1.5 meals, bob: 0.5 meals → total = 2.0, expenses = 200 → rate = 100
        val meals = listOf(
            MealEntry(alice, 1.0, 0.5, 0.0),
            MealEntry(bob,   0.5, 0.0, 0.0)
        )
        val expenses = listOf(ExpenseEntry(200.0))
        val deposits = listOf(DepositEntry(alice, 150.0), DepositEntry(bob, 50.0))

        val result = DuesCalculator.calculate(meals, expenses, deposits)

        assertEquals(100.0, result.mealRate)
        val a = result.balances.first { it.memberUserId == alice }
        assertEquals(1.5,   a.totalMeals)
        assertEquals(150.0, a.mealCost)
        assertEquals(0.0,   a.balance)

        val b = result.balances.first { it.memberUserId == bob }
        assertEquals(0.5,  b.totalMeals)
        assertEquals(50.0, b.mealCost)
        assertEquals(0.0,  b.balance)
    }
}
