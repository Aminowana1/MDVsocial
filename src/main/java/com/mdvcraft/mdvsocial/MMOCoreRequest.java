package com.mdvcraft.mdvsocial;

import java.util.UUID;

/**
 * A pending MMOCore request (party invite, friend request) read through reflection.
 *
 * @param handle      the MMOCore object, used to accept/deny it
 * @param kind        MMOCore class simple name ("PartyInvite", "FriendRequest")
 * @param id          MMOCore's own request id; its accept/deny commands expect this one
 * @param creator     who sent it
 * @param creatorName display name of the sender
 * @param target      who received it
 */
record MMOCoreRequest(Object handle, String kind, UUID id, UUID creator, String creatorName, UUID target) {
}
