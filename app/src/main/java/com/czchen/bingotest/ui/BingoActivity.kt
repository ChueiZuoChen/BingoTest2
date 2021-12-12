package com.czchen.bingotest.ui

import android.app.AlertDialog
import android.content.DialogInterface
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.czchen.bingotest.NumberButton
import com.czchen.bingotest.R
import com.czchen.bingotest.databinding.ActivityBingoBinding
import com.czchen.bingotest.databinding.SingleButtonBinding
import com.firebase.ui.common.ChangeEventType
import com.firebase.ui.database.FirebaseRecyclerAdapter
import com.firebase.ui.database.FirebaseRecyclerOptions
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class BingoActivity : AppCompatActivity(), View.OnClickListener {
    companion object {
        private val TAG = "BingoActivity"
        val STATUS_INIT = 0
        val STATUS_CREATED = 1
        val STATUS_JOINED = 2
        val STATUS_CREATOR_TURN = 3
        val STATUS_JOINER_TURN = 4
        val STATUS_CREATOR_BINGO = 5
        val STATUS_JOINER_BINGO = 6
    }

    private lateinit var adapter: FirebaseRecyclerAdapter<Boolean, NumberHolder>
    lateinit var roomId: String
    var isCreator: Boolean = false
    lateinit var binding: ActivityBingoBinding

    var myTurn: Boolean = false
        set(value) {
            field = value
            binding.info.text = if (value) "Pick a number" else "Waiting for Competitor"
        }
    private val statusListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            var status = snapshot.value as Long
            when(status.toInt()){
                STATUS_CREATED -> {
                    binding.info.setText("Waiting for join...")
                }
                STATUS_JOINED ->{
                    binding.info.setText("User joined")
                    FirebaseDatabase.getInstance().getReference("rooms")
                        .child(roomId)
                        .child("status")
                        .setValue(STATUS_CREATOR_TURN)
                }
                STATUS_CREATOR_TURN ->{
                    myTurn = isCreator

                }
                STATUS_JOINER_TURN ->{
                    myTurn = !isCreator
                }
                STATUS_CREATOR_BINGO ->{
                    AlertDialog.Builder(this@BingoActivity)
                        .setTitle("Bingo")
                        .setMessage(if (isCreator) "Congratulations,You Bingo!" else "Competitor Bingo!")
                        .setPositiveButton("OK", DialogInterface.OnClickListener { dialog, which ->
                            endGame()
                        }).show()
                }
                STATUS_JOINER_BINGO ->{
                    AlertDialog.Builder(this@BingoActivity)
                        .setTitle("Bingo")
                        .setMessage(if (!isCreator) "Congratulations,You Bingo!" else "Competitor Bingo!")
                        .setPositiveButton("OK", DialogInterface.OnClickListener { dialog, which ->
                            endGame()
                        }).show()
                }


            }
        }

        override fun onCancelled(error: DatabaseError) {

        }

    }

    private fun endGame() {
        FirebaseDatabase.getInstance().getReference("rooms")
            .child(roomId)
            .child("status")
            .removeEventListener(statusListener)
        if (isCreator){
            FirebaseDatabase.getInstance().getReference("rooms")
                .child(roomId)
                .removeValue()
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBingoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        isCreator = intent.getBooleanExtra("IS_CREATOR", false)
        roomId = intent.getStringExtra("ROOM_ID")!!
        Log.d(TAG, " roomId: $roomId isCreator: $isCreator")

        if(isCreator) {
            for (i in 1..25) {
                FirebaseDatabase.getInstance().getReference("rooms")
                    .child(roomId)
                    .child("number")
                    .child(i.toString())
                    .setValue(false)
            }

            FirebaseDatabase.getInstance().getReference("rooms")
                .child(roomId)
                .child("status")
                .setValue(STATUS_CREATED)
        }else{
            FirebaseDatabase.getInstance().getReference("rooms")
                .child(roomId)
                .child("status")
                .setValue(STATUS_JOINED)
        }


        val numberMap = HashMap<Int,Int>()
        val buttons = mutableListOf<NumberButton>()
        for(i in 0..24 ){
            val button = NumberButton(this)
            button.number = i+1
            buttons.add(button)
        }
        buttons.shuffle()
        for(i in 0..24){
            numberMap.put(buttons.get(i).number,i)
        }
        binding.recyclerBingo.setHasFixedSize(true)
        binding.recyclerBingo.layoutManager = GridLayoutManager(this,5)


        //adapter
        val query = FirebaseDatabase.getInstance().getReference("rooms")
            .child(roomId)
            .child("number")
            .orderByKey()
        val option = FirebaseRecyclerOptions.Builder<Boolean>()
            .setQuery(query, Boolean::class.java)
            .build()

        adapter = object : FirebaseRecyclerAdapter<Boolean, NumberHolder>(option) {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NumberHolder {
                val view =
                    SingleButtonBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                return NumberHolder(view)
            }

            override fun onBindViewHolder(holder: NumberHolder, position: Int, model: Boolean) {
                holder.button.text = buttons[position].number.toString()
                holder.button.number = buttons[position].number
                holder.button.isEnabled = !buttons[position].picked
                holder.button.setOnClickListener(this@BingoActivity)

            }
            override fun onChildChanged(type: ChangeEventType,
                                        snapshot: DataSnapshot,
                                        newIndex: Int,
                                        oldIndex: Int)
            {
                super.onChildChanged(type, snapshot, newIndex, oldIndex)
                Log.d(TAG, "onChildChanged:$type / $snapshot / ${snapshot.key} / ${snapshot.getValue()}")
                if (type == ChangeEventType.CHANGED){
                    var number : Int? = snapshot.key?.toInt()
                    var picked : Boolean = snapshot.getValue() as Boolean
                    var pos : Int? = numberMap.get(number)
                    buttons.get(pos!!).picked =  picked
                    var holder : NumberHolder = binding.recyclerBingo.findViewHolderForAdapterPosition(pos) as NumberHolder
                    holder.button.isEnabled = !picked


                    val nums = IntArray(25)

                    for(i in 0..24){
                        nums[i] = if(buttons.get(i).picked) 1 else 0
                    }
                    var bingo = 0
                    for(i in 0..4){
                        var sum = 0
                        for (j in 0..4){
                            sum +=nums[i*5+j]
                        }
                        bingo += if (sum==5) 1 else 0
                        sum = 0
                        for (j in 0..4){
                            sum +=nums[j*5+i]
                        }
                        bingo += if (sum==5) 1 else 0

//                        Log.d(TAG, "onChildChanged: $bingo")
                        if(bingo > 0){
                            when(isCreator){
                                true -> FirebaseDatabase.getInstance().getReference("rooms")
                                    .child(roomId)
                                    .child("status")
                                    .setValue(STATUS_CREATOR_BINGO)
                                false ->  FirebaseDatabase.getInstance().getReference("rooms")
                                    .child(roomId)
                                    .child("status")
                                    .setValue(STATUS_JOINER_BINGO)
                            }
                        }
                    }
                    var sum2 = 0
                    for (i in 0..4){
                        if(nums[i*6] == 1) sum2++
                    }
                    var sum3 = 0
                    for (i in 1..5){
                        if (nums[i*4]==1) sum3++
                    }
                    if (sum2==5 || sum3 == 5){
                        when(isCreator){
                            true -> FirebaseDatabase.getInstance().getReference("rooms")
                                .child(roomId)
                                .child("status")
                                .setValue(STATUS_CREATOR_BINGO)
                            false ->  FirebaseDatabase.getInstance().getReference("rooms")
                                .child(roomId)
                                .child("status")
                                .setValue(STATUS_JOINER_BINGO)
                        }
                    }
                }
            }
        }
        binding.recyclerBingo.adapter = adapter

    }


    //viewholder
    class NumberHolder(binding: SingleButtonBinding) : RecyclerView.ViewHolder(binding.root) {
        var button: NumberButton = binding.root.findViewById(R.id.button)
    }

    override fun onStart() {
        super.onStart()
        adapter.startListening()
        FirebaseDatabase.getInstance().getReference("rooms")
            .child(roomId)
            .child("status")
            .addValueEventListener(statusListener)
    }

    override fun onStop() {
        super.onStop()
        adapter.stopListening()
        FirebaseDatabase.getInstance().getReference("rooms")
            .child(roomId)
            .child("status")
            .removeEventListener(statusListener)
    }

    override fun onClick(v: View?) {
        if(myTurn){
            val number = (v as NumberButton).number
            FirebaseDatabase.getInstance().getReference("rooms")
                .child(roomId)
                .child("number")
                .child(number.toString())
                .setValue(true)

            FirebaseDatabase.getInstance().getReference("rooms")
                .child(roomId)
                .child("status")
                .setValue(if (isCreator) STATUS_JOINER_TURN else STATUS_CREATOR_TURN)
        }
    }

}