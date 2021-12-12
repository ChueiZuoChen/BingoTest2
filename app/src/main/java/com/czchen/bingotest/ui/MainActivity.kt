package com.czchen.bingotest.ui

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.czchen.bingotest.GameRoom
import com.czchen.bingotest.Member
import com.czchen.bingotest.R
import com.czchen.bingotest.databinding.ActivityMainBinding
import com.czchen.bingotest.databinding.RowRoomBinding
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.database.FirebaseRecyclerAdapter
import com.firebase.ui.database.FirebaseRecyclerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.*

class MainActivity : AppCompatActivity(), FirebaseAuth.AuthStateListener, View.OnClickListener {

    private lateinit var adapter: FirebaseRecyclerAdapter<GameRoom, RoomHolder>
    lateinit var binding: ActivityMainBinding
    var member: Member? = null

    companion object {
        private const val RC_SIGN_IN = 100
        private const val TAG = "MainActivity"
    }


    var avatarIds = intArrayOf(
        R.drawable.avatar_0,
        R.drawable.avatar_1,
        R.drawable.avatar_2,
        R.drawable.avatar_3,
        R.drawable.avatar_4,
        R.drawable.avatar_5,
        R.drawable.avatar_6
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.nickname.setOnClickListener {
            val nickname = binding.nickname.text.toString()
            FirebaseAuth.getInstance().currentUser?.let {
                showNickNameDialog(it.uid, nickname)
            }
        }

        binding.groupAvatars.visibility = View.GONE
        binding.avatar.setOnClickListener {
            binding.groupAvatars.visibility =
                if (binding.groupAvatars.visibility == View.GONE) View.VISIBLE else View.GONE
        }

        //icon pick
        binding.avatar0.setOnClickListener(this)
        binding.avatar1.setOnClickListener(this)
        binding.avatar2.setOnClickListener(this)
        binding.avatar3.setOnClickListener(this)
        binding.avatar4.setOnClickListener(this)
        binding.avatar5.setOnClickListener(this)
        binding.avatar6.setOnClickListener(this)

        binding.fab.setOnClickListener {
            val roomText = EditText(this)
            roomText.setText("Room")
            AlertDialog.Builder(this)
                .setTitle("Create new room")
                .setMessage("Room title?")
                .setView(roomText)
                .setPositiveButton("OK") { dialog, which ->
                    var room = GameRoom(roomText.text.toString(), member)
                    FirebaseDatabase.getInstance().getReference("rooms")
                        .push().setValue(room, object : DatabaseReference.CompletionListener {
                            override fun onComplete(error: DatabaseError?, databaseReference: DatabaseReference) {
                                val roomId = databaseReference.key
                                FirebaseDatabase.getInstance().getReference("rooms")
                                    .child(roomId.toString())
                                    .child("id")
                                    .setValue(roomId)


                                val bingo = Intent(this@MainActivity,BingoActivity::class.java)
                                bingo.putExtra("ROOM_ID",roomId)
                                bingo.putExtra("IS_CREATOR", true)
                                startActivity(bingo)
                            }

                        })

                }
                .setNeutralButton("Cancel",null)
                .show()
        }
        binding.roomRecycler.setHasFixedSize(true)
        binding.roomRecycler.layoutManager = LinearLayoutManager(this)
        val query = FirebaseDatabase.getInstance().getReference("rooms").limitToLast(30)
        val options = FirebaseRecyclerOptions.Builder<GameRoom>()
            .setQuery(query,GameRoom::class.java)
            .build()

        adapter = object : FirebaseRecyclerAdapter<GameRoom,RoomHolder>(options) {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoomHolder {
                val view = RowRoomBinding.inflate(LayoutInflater.from(parent.context),parent,false)
                return RoomHolder(view)
            }

            override fun onBindViewHolder(holder: RoomHolder, position: Int, model: GameRoom) {
                holder.image.setImageResource(avatarIds[model.init!!.avatarId])
                holder.title.text = model.title
                holder.itemView.setOnClickListener {
                    val bingo = Intent(this@MainActivity,BingoActivity::class.java)
                    bingo.putExtra("ROOM_ID",model.id)
                    startActivity(bingo)
                }
            }

        }

        binding.roomRecycler.adapter = adapter

    }

    class RoomHolder(binding: RowRoomBinding) : RecyclerView.ViewHolder(binding.root){
        var image = binding.roomImage
        var title = binding.roomTitle
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_signout -> {
                FirebaseAuth.getInstance().signOut()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onStop() {
        super.onStop()
        FirebaseAuth.getInstance().removeAuthStateListener(this)
        adapter.stopListening()
    }

    override fun onStart() {
        super.onStart()
        FirebaseAuth.getInstance().addAuthStateListener(this)
        adapter.startListening()
    }

    override fun onAuthStateChanged(auth: FirebaseAuth) {
        auth.currentUser?.also {
            //already login
            Log.d(TAG, "currentUser: ${it.uid}")
            Log.d(TAG, "currentUser: ${it.displayName}")

            it.displayName?.run {
                FirebaseDatabase.getInstance()
                    .getReference("users")
                    .child(it.uid)
                    .child("displayName")
                    .setValue(this)
                    .addOnCompleteListener {
                        Log.d(TAG, "done")
                    }
            }

            //頃聽整筆會員資料
            FirebaseDatabase.getInstance().getReference("users")
                .child(it.uid)
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        member = snapshot.getValue(Member::class.java)
                        member?.uid = it.uid
                        member?.nickname?.also { nickName ->
                            binding.nickname.text = nickName
                        } ?: showNickNameDialog(it)

                        member?.avatarId?.also { avatarId ->
                            binding.avatar.setImageResource(avatarIds[avatarId])
                        } ?: binding.avatar.setImageResource(avatarIds[0])
                    }

                    override fun onCancelled(error: DatabaseError) {

                    }
                })

            //頃聽單筆會員資料
            /* FirebaseDatabase.getInstance()
                 .getReference("users")
                 .child(it.uid)
                 .child("nickname")
                 .addListenerForSingleValueEvent(object : ValueEventListener {
                     override fun onDataChange(dataSnapshot: DataSnapshot) {
                         dataSnapshot.value?.also { nickname ->
                             Log.d(TAG, "nickname $nickname")
                         } ?: showNickNameDialog(it)
                     }

                     override fun onCancelled(error: DatabaseError) {

                     }
                 })*/

        } ?: signUp()
    }

    private fun showNickNameDialog(uid: String, nickName: String?) {
        val editText = EditText(this)
        editText.setText(nickName)
        AlertDialog.Builder(this)
            .setTitle("Nickname")
            .setMessage("Your nickname?")
            .setView(editText)
            .setPositiveButton("OK") { dialog, which ->
                FirebaseDatabase.getInstance().getReference("users")
                    .child(uid)
                    .child("nickname")
                    .setValue(editText.text.toString())
            }.show()
    }

    private fun showNickNameDialog(user: FirebaseUser) {
        val nickname = user.displayName.toString()
        showNickNameDialog(user.uid, nickname)
    }

    private fun signUp() {
        startActivityForResult(
            AuthUI.getInstance().createSignInIntentBuilder()
                .setAvailableProviders(
                    listOf(
                        AuthUI.IdpConfig.EmailBuilder().build(),
                        AuthUI.IdpConfig.GoogleBuilder().build()
                    )
                )
                .setIsSmartLockEnabled(false)
                .build(), RC_SIGN_IN
        )
    }

    override fun onClick(avatar: View?) {
        val selectId = when (avatar!!.id) {
            R.id.avatar_0 -> 0
            R.id.avatar_1 -> 1
            R.id.avatar_2 -> 2
            R.id.avatar_3 -> 3
            R.id.avatar_4 -> 4
            R.id.avatar_5 -> 5
            R.id.avatar_6 -> 6
            else -> 0
        }
        FirebaseDatabase.getInstance()
            .getReference("users")
            .child(FirebaseAuth.getInstance().currentUser!!.uid)
            .child("avatarId")
            .setValue(selectId)


        binding.groupAvatars.visibility = View.GONE
        binding.avatar.setImageResource(avatarIds[selectId])
    }
}